import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class TACEvaluator {

	// NOTE: simply change this value
	static String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
	static String docDir = "/Users/christanner/research/projects/TAC2014/TAC_2014_BiomedSumm_Training_Data/";
	static boolean runLDA = false;
	static int numSentencesPerCitance = 5;
	
	static Set<String> stopwords;
	
	// LDA's input files
	static String annoInputFile = dataDir + "annoLegend.txt";
	static String malletInputFile = dataDir + "mallet-tac.txt";
	static String stopwordsFile = dataDir + "stopwords.txt";

	
	// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
	static String ldaObject = dataDir + "lda_2000i.ser";
	
	public static void main(String[] args) throws IOException {
	
		// create Documents (currently just each Source gets made into a Document, not the reports that cite it)
		stopwords = loadStopwords(stopwordsFile);
		Map<String, Document> docs = loadReferenceDocuments(annoInputFile);
		Set<Citance> citances = loadCitances(annoInputFile);
		
		Map<Citance, List<IndexPair>> jaccardPredictions = getJaccardPredictions(docs, citances);
		
		// NOTE: LDA variables/params are in the LDA's class as global vars
		if (runLDA) {
			LDA l = new LDA(malletInputFile, stopwordsFile);
			l.runLDA();
			l.saveLDA(ldaObject);
		}
	}

	// for each Citance, returns a ranking of the Reference's sentences
	// (NOTE: the target/golden answers could technically be
	// sentence fragments, but this seems rare and would complicate things a lot)
	private static Map<Citance, List<IndexPair>> getJaccardPredictions(Map<String, Document> docs, Set<Citance> citances) {
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			Set<String> citanceTypes = removeStopwords(c.getTextTokensAsSet());
			System.out.println("citance types:" + citanceTypes);
			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			//System.out.println(c.referenceDoc);
			//System.out.println(docs);
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwords(s.types);
				//System.out.println("sentence types:" + curReferenceTypes);
				double score = 0;
				int intersection  = 0;
				for (String token : curReferenceTypes) {
					if (citanceTypes.contains(token)) {
						intersection++;
					}
				}
				// ensures both the citance and reference sentences aren't just stopwords
				if (curReferenceTypes.size() > 0 && citanceTypes.size() > 0 ) {
					score = (double)intersection / ((double)(citanceTypes.size() + curReferenceTypes.size() - intersection));
				}
				
				sentenceScores.put(s, score);
			}
			
			System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueDescending(sentenceScores).keySet().iterator();
			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				//System.out.println("score:" + sentenceScores.get(s) + ": " + s.sentence);
			}
			ret.put(c, sentenceMarkers);
			
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	static Map sortByValueDescending(Map map) {
		List list = new LinkedList(map.entrySet());
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o2)).getValue()).compareTo(((Map.Entry) (o1)).getValue());
			}
		});

		Map result = new LinkedHashMap();
		for (Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private static Set<String> removeStopwords(Set<String> tokens) {
		Set<String> ret = new HashSet<String>();
		for (String t : tokens) {
			if (!stopwords.contains(t)) {
				ret.add(t);
			}
		}
		return ret;
	}

	// creates each of the unique Citances, where a Citance also stores each Annotation that was provided for it (i.e., typically 4 per Citance)
	static Set<Citance> loadCitances(String annoInputFile) throws IOException {
		Set<Citance> ret = new HashSet<Citance>();
		Map<String, Citance> uidToCitance = new HashMap<String, Citance>();
		
		
		BufferedReader bin = new BufferedReader(new FileReader(annoInputFile));
		String curLine = "";
		
		// reads each line of annoInputFile, while looking for 'topicID' and 'reference article' fields,
		// in order to know the proper location of the file for which we wish to create a Document
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine, "|");
			String topicID = "";
			int citanceNum = 0;
			String referenceDoc = "";
			String citingDoc = "";
			String citationText = ""; // we try to remove all text within ( ) because it usually contains author info
			
			List<String> refOffsets = new ArrayList<String>();
			String refText = "";
			String discourse = "";
			String annotator = "";
			while (st.hasMoreTokens()) {

				String field = st.nextToken().trim();
				//System.out.println("t:" + field);
				if (field.toLowerCase().startsWith("topic id:")) {
					String[] tokens = field.split(" ");
					topicID = tokens[2];
				} else if (field.toLowerCase().startsWith("citance number")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					citanceNum = Integer.parseInt(st2.nextToken());
					
				} else if (field.toLowerCase().startsWith("reference article:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					referenceDoc = st2.nextToken();
				} else if (field.toLowerCase().startsWith("citing article:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					citingDoc = st2.nextToken();
				} else if (field.toLowerCase().startsWith("citation text:")) {
					citationText = field.substring(15).trim();
					//System.out.println("cittext:" + citationText);
				} else if (field.toLowerCase().startsWith("reference offset:")) {
					String right = field.substring(18);
					StringTokenizer st2 = new StringTokenizer(right, "[', ]");
					
					while (st2.hasMoreTokens()) {
						String ro = st2.nextToken();
						//System.out.println("ro:" + ro);
						refOffsets.add(ro);
					}
					//System.out.println("offsets:" + right);
				} else if (field.toLowerCase().startsWith("reference text:")) {
					refText = field.substring(15).trim();
					//System.out.println("reftext:" + refText);
				} else if (field.toLowerCase().startsWith("discourse facet:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					discourse = st2.nextToken();
				} else if (field.toLowerCase().startsWith("annotator:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					annotator = st2.nextToken();
				}
			}
			
			String uid = topicID + "_" + Integer.toString(citanceNum);
			Citance c = null;
			if (uidToCitance.containsKey(uid)) {
				c = uidToCitance.get(uid);
			} else {
				c = new Citance(topicID, citanceNum, referenceDoc, citingDoc, citationText);
			}

			Annotation a = new Annotation(refOffsets, refText, discourse, annotator);
			System.out.println(a);
			c.addAnnotation(a);
			if (!uidToCitance.containsKey(uid)) {
				uidToCitance.put(uid, c);
				ret.add(c);
			}
		}
		return ret;
	}


	// loads the ref docs
	static Map<String, Document> loadReferenceDocuments(String annoInputFile) throws IOException {
		Map<String, Document> ret = new HashMap<String, Document>();
		
		BufferedReader bin = new BufferedReader(new FileReader(annoInputFile));
		String curLine = "";
		
		// reads each line of annoInputFile, while looking for 'topicID' and 'reference article' fields,
		// in order to know the proper location of the file for which we wish to create a Document
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine, "|");
			String topicID = "";
			String sourceName = "";

			while (st.hasMoreTokens()) {

				String field = st.nextToken().trim();
				//System.out.println("t:" + field);
				if (field.startsWith("Topic ID:")) {
					String[] tokens = field.split(" ");
					topicID = tokens[2];
				} else if (field.startsWith("Reference Article:")) {
					//System.out.println("ref:" + field);
					StringTokenizer st2 = new StringTokenizer(field);
					//String[] tokens = field.split(" ");
					//sourceName = tokens[2];
					st2.nextToken();
					st2.nextToken();
					sourceName = st2.nextToken();
					//System.out.println("source: " + sourceName);
				}
			}
			
			// constructs a new Document
			if (!topicID.equals("") && !sourceName.equals("")) {
				String uid = topicID + ":" + sourceName;
				if (!ret.containsKey(uid)) {
					Document d = new Document(docDir, topicID, sourceName);
					System.out.println(d);
					ret.put(uid, d);
				}
			}
		}
		return ret;
	}
	
	// loads a list
	public static Set<String> loadStopwords(String listFile) throws IOException {
		Set<String> ret = new HashSet<String>();
		if (listFile != null) {
			BufferedReader bin = new BufferedReader(new FileReader(listFile));
			String curLine = "";
			while ((curLine = bin.readLine()) != null) {
				ret.add(curLine);
			}
		}
		return ret;
	}
	
 	// represents the start and end character positions of a sentence, relative to the containing doc (the reference doc)
 	static class IndexPair {
 		int startPos;
 		int endPos;
 		IndexPair(int s, int e) {
 			this.startPos = s;
 			this.endPos = e;
 		}
 		public String toString() {
 			return startPos + "-" + endPos;
 		}
 	}
}
