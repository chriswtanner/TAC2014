import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
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
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import cc.mallet.util.CommandOption.File;

public class TACEvaluator {

	// NOTE: simply change this value
	static String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
	static String docDir = "/Users/christanner/research/projects/TAC2014/TAC_2014_BiomedSumm_Training_Data_V1.2/"; //TAC_2014_BiomedSumm_Training_Data/";
	static boolean runLDA = false;
	static String method = "jaccard"; //lda";
	static Integer minNumDocs = 2;
	static Double maxPercentDocs = .8;
	
	static Set<String> stopwords;
	
	// output
	static String resultsOut = dataDir + "results.csv";
	static String statsOut = dataDir + "stats.csv";
	
	// LDA's input files
	static String annoInputFile = dataDir + "annoLegend.txt";
	static String malletInputFile = dataDir + "mallet-tac.txt";
	static String stopwordsFile = dataDir + "stopwords.txt";

	static Map<String, Document> docs = new HashMap<String, Document>();
	
	// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
	static String ldaObject = dataDir + "lda_25z_2000i.ser";
	
	static Set<String> badWords = new HashSet<String>();
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
	
		stopwords = loadStopwords(stopwordsFile);
		
		makeMalletFileAndGetBadWords(malletInputFile);
		
		//System.exit(1);
		// create Documents (currently just each Source gets made into a Document, not the reports that cite it)
		docs = loadReferenceDocuments(annoInputFile);

		Set<Citance> citances = loadCitances(annoInputFile);
		
		// just checks citances
		System.out.println(docs.keySet());
		int badAnno = 0;
		int goodAnno = 0;
		int mostOff = 0;
		for (Citance c : citances) {

			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (1==1 || d.name.equals("Blasco.txt")) {
				//System.out.println("citance: " + c);
				//System.out.println("in doc:" + d.name);
				
				for (Annotation a : c.annotations) {
					boolean perfect = true;
					List<String> golden = new ArrayList<String>();
					String ansText = a.referenceText;
					//System.out.println("ansText:" + ansText + "END");
					List<String> refList = new ArrayList<String>();
					while (ansText.indexOf("...") != -1) {
						int pos = ansText.indexOf("... ");
						String beg = ansText.substring(0,pos-1);
						refList.add(beg);
						//System.out.println("beg:" + beg + "END");
						ansText = ansText.substring(pos + 4);
						//golden.add(ansText);
					}
					
					refList.add(ansText.trim());
					//golden.add(ansText);
					for (String r : refList) {
						//System.out.println("gold (" + r.length() + "):" + r);
						golden.add(r);
					}
					
					//System.out.println("gold: " + ansText);
					for (IndexPair i : a.referenceOffsets) {
						String refText = d.originalText.substring(i.startPos, i.endPos-1);
						System.out.println(i.startPos + "-" + i.endPos);
						//System.out.println("\tref:" + refText);
						//String s = new String(d.originalBuffer, "UTF-8");
						//System.out.println("\tbuf-to-str:" + s.substring(i.startPos,i.endPos));
						//System.out.println("\tstr:" + d.originalText.substring(i.startPos,i.endPos));
						String myRefGuess = d.originalText.substring(i.startPos,i.endPos);
						boolean contains = false;
						String closest = "";
						for (String gold : golden) {
							if (gold.equals(myRefGuess)) {
								contains = true;
							}
							if (Math.abs(gold.length() - myRefGuess.length()) < Math.abs(myRefGuess.length() - closest.length())) {
								closest = gold;
							}
						}
						if (!contains) {
							perfect = false;
							int numOff = 0;
							//System.out.println("NOT CONTAINED (" + myRefGuess.length() + "):" + myRefGuess);
							//System.out.println("closest:" + closest);
							for (int x=0; x<Math.min(myRefGuess.length(), closest.length()); x++) {
								if (myRefGuess.charAt(x) != closest.charAt(x)) {
									numOff++;
									//System.out.println("we disagree on char " + x + ": "  + myRefGuess.charAt(x) + " vs " + closest.charAt(x));
									//break;
								}
							}
							//System.out.println("numOff: " + numOff);
							if (numOff > mostOff) {
								mostOff = numOff;
							}
						} else {
							//System.out.println("CONTAINED:" + myRefGuess);
						}
					}
					if (perfect) {
						goodAnno++;
					} else {
						badAnno++;
					}
				}

				//System.exit(1);
			}
		}
		System.out.println("most off: " + mostOff);
		System.out.println("good anno: " + goodAnno);
		System.out.println("bad anno: " + badAnno);
		System.exit(1);
		// NOTE: LDA variables/params are in the LDA's class as global vars
		if (runLDA) {
			LDA l = new LDA(malletInputFile, stopwordsFile);
			l.runLDA();
			l.saveLDA(ldaObject);
		}
		//System.exit(1);
		
		// runs the lda-saved model to create topics per sentence
		Map<Citance, List<IndexPair>> predictions = null;
		if (method.equals("lda")) {
			
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(ldaObject));
			TopicModelObject tmo = (TopicModelObject)in.readObject();
			
			predictions = getLDAPredictions(docs, citances, tmo);
		} else if (method.equals("jaccard")) {
			predictions = getJaccardPredictions(docs, citances);
		} else if (method.equals("longest")) {
			predictions = getLongestStringPredictions(docs, citances);
		} else if (method.equals("perfect")) {
			predictions = getPerfectPredictions(docs, citances);
		}
		
		List<Double> recall = scorePredictions(predictions);
		//displayStats(predictions);
		//printSimilarityStats(predictions, 50);
	}


	private static void makeMalletFileAndGetBadWords(String malletOutput) throws IOException {
		//Map<String, Document> ret = new HashMap<String, Document>();
		

		BufferedReader bin = new BufferedReader(new FileReader(annoInputFile));
		String curLine = "";
		Map<String, Document> docs = new HashMap<String, Document>();
		
		// reads each line of annoInputFile, while looking for 'topicID' and 'reference article' fields,
		// in order to know the proper location of the file for which we wish to create a Document
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine, "|");
			String topicID = "";
			String doc = "";

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
					doc = st2.nextToken();
					//String id = topicID + "_" + doc;
					if (!docs.containsKey(topicID + "_" + doc)) {
						Document d = new Document(docDir, topicID, doc);
						docs.put(topicID + "_" + doc, d);
					}
					//System.out.println("source: " + sourceName);
				} else if (field.startsWith("Citing Article:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					doc = st2.nextToken();
					if (!docs.containsKey(topicID + "_" + doc)) {
						Document d = new Document(docDir, topicID, doc);
						docs.put(topicID + "_" + doc, d);
					}
					//System.out.println("CITING ARTICLE!: " + doc);
				}
			}
		}
		
		// goes through all docs (citing and reference docs) and writes to the mallet output file
		BufferedWriter bout = new BufferedWriter(new FileWriter(malletOutput));
		
		Map<String, Set<String>> wordToDocs = new HashMap<String, Set<String>>();
		
		// goes through once to first find all bad words
		for (String doc : docs.keySet()) {
			Set<String> docTypes = new HashSet<String>();
			
			Document d = docs.get(doc);
			for (Sentence s : d.sentences) {
				List<String> curReferenceTypes = removeStopwordsAndBadWords(s.tokens);
				docTypes.addAll(curReferenceTypes);
			}
			
			for (String w : docTypes) {
				Set<String> tmp = new HashSet<String>();
				if (wordToDocs.containsKey(w)) {
					tmp = wordToDocs.get(w);
				}
				tmp.add(doc);
				wordToDocs.put(w, tmp);
			}
		}
		for (String w : wordToDocs.keySet()) {
			int numDocs = wordToDocs.get(w).size();
			double percent = (double)numDocs / (double)docs.keySet().size();
			if (numDocs < minNumDocs || percent > maxPercentDocs || w.length() == 1) {
				badWords.add(w);
			}
		}
		System.out.println(badWords.size() + " of " + wordToDocs.keySet().size() + " (" + (double)badWords.size()/(double)wordToDocs.keySet().size()  +") words were bad: " + badWords);
		
		// goes through again and writes the mallet file for the good words
		for (String doc : docs.keySet()) {

			Document d = docs.get(doc);
			bout.write(d.topicID + "_" + d.name + " " + d.topicID + "_" + d.name);
			for (Sentence s : d.sentences) {
				if (d.name.equals("Blasco.txt")) {
					System.out.println("blasco sent: " + s.sentence);
				}
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);
				for (String w : curReferenceTokens) {
					if (!badWords.contains(w)) {
						bout.write(" " + w);
					}
				}
			}
			bout.write("\n");
		}
		bout.close();
	}


	private static String getMalletLine(String docDir, String topicID, String doc) {
		// TODO Auto-generated method stub
		String ret = "";
		
		return ret;
	}


	private static void printSimilarityStats(Map<Citance, List<IndexPair>> predictions, int numBuckets) throws IOException {
		
		BufferedWriter bout = new BufferedWriter(new FileWriter(statsOut));
		double maxThreshold = 0.5;
		double avgThreshold = 0.1;
		
		// prints x-y plot for the golden annotations (x=bin #, y=count)
		int[] bins = new int[numBuckets]; // for the jaccard 
		List<Double> perfectScores = new ArrayList<Double>();
		List<Double> maxGoodScores = new ArrayList<Double>();
		List<Double> maxBadScores = new ArrayList<Double>();
		List<Double> avgGoodScores = new ArrayList<Double>();
		List<Double> avgBadScores = new ArrayList<Double>();
		double max = 0;
		
		// fills in the perfect, golden annotations
		for (Citance c : predictions.keySet()) {
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			for (Annotation a : c.annotations) {
				
				Set<String> ret = new HashSet<String>();
				StringTokenizer st = new StringTokenizer(a.referenceText.toLowerCase(), " ,.;\"");
				while (st.hasMoreTokens()) {
					ret.add(st.nextToken());
				}
				Set<String> annoTypes = removeStopwordsAndBadWords(ret);
				double score = 0;
				int intersection  = 0;
				for (String token : annoTypes) {
					if (citanceTypes.contains(token)) {
						intersection++;
					}
				}
				// ensures both the citance and reference sentences aren't just stopwords
				if (annoTypes.size() > 0 && citanceTypes.size() > 0 ) {
					score = (double)intersection / ((double)(citanceTypes.size() + annoTypes.size() - intersection));
				}
				perfectScores.add(score);
				
				if (score > max) {
					max = score;
				}
				//System.out.println(a.referenceText.toLowerCase() + " => " + annoTypes);
			}
		}
		
		max = 0; // resets the max because we'll graph the max/avg plots separately, using their own max to determine the bins/buckets
		// gets max scores
		for (Citance c : predictions.keySet()) {
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
						
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences");
				System.exit(1);
			}
			// for each sentence, determines the max fill percentage across Annos and the avg fill perctange across Annos
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				double maxAnnoFill = 0;
				double avgAnnoFill = 0;
				for (Annotation a : c.annotations) {
					double fillPercentage = a.theoreticFillInSentence(s.startPos, s.endPos); // the best reference offset only is returned
					
					if (fillPercentage > maxAnnoFill) {
						maxAnnoFill = fillPercentage;
					}
					
					avgAnnoFill += fillPercentage;
				}
				avgAnnoFill /= c.annotations.size();
				
				// gets jaccard sim score for the given sentence
				double score = 0;
				int intersection = 0;
				for (String token : curReferenceTypes) {
					if (citanceTypes.contains(token)) {
						intersection++;
					}
				}
				// ensures both the citance and reference sentences aren't just stopwords
				if (curReferenceTypes.size() > 0 && citanceTypes.size() > 0 ) {
					score = (double)intersection / ((double)(citanceTypes.size() + curReferenceTypes.size() - intersection));
				}
				
				if (score > max) {
					max = score;
				}
				if (score == 1) {
					System.out.println("**** max score is 1!!");
				}
				if (maxAnnoFill >= maxThreshold) {
					maxGoodScores.add(score);
				} else {
					maxBadScores.add(score);
				}
				
				if (avgAnnoFill >= avgThreshold) {
					avgGoodScores.add(score);
				} else {
					avgBadScores.add(score);
				}
			}

		}
		
		// puts scores into their respective buckets
		double bucketSize = (double)max / (double)numBuckets;
		System.out.println("bucket size: " + bucketSize);
		int[] maxGoodBins = new int[numBuckets];
		int[] maxBadBins = new int[numBuckets];
		int[] avgGoodBins = new int[numBuckets];
		int[] avgBadBins = new int[numBuckets];
		
		for (double score : maxGoodScores) {
			for (int i=0; i<numBuckets; i++) {
				if (score >= i*bucketSize && score <= (i+1)*bucketSize) {
					maxGoodBins[i]++;
					break;
				}
			}
		}
		for (double score : maxBadScores) {
			for (int i=0; i<numBuckets; i++) {
				if (score >= i*bucketSize && score <= (i+1)*bucketSize) {
					maxBadBins[i]++;
					break;
				}
			}
		}
		for (double score : avgGoodScores) {
			for (int i=0; i<numBuckets; i++) {
				if (score >= i*bucketSize && score <= (i+1)*bucketSize) {
					avgGoodBins[i]++;
					break;
				}
			}
		}
		for (double score : avgBadScores) {
			for (int i=0; i<numBuckets; i++) {
				if (score >= i*bucketSize && score <= (i+1)*bucketSize) {
					avgBadBins[i]++;
					break;
				}
			}
		}
		System.out.println("total maxgoodscores: " + maxGoodScores.size() + "; max: " + max);
		bout.write("jaccard sim, # sentences whose max anno > " + maxThreshold + ", # sentences whose max anno < " + maxThreshold + ", # sentences who avg anno > " + avgThreshold + ", # sentences who avg anno < " + avgThreshold + "\n");
		for (int i=0; i<numBuckets; i++) {
			bout.write(i*bucketSize + "-" + (i+1)*bucketSize + "," + maxGoodBins[i] + "," + maxBadBins[i] + "," + avgGoodBins[i] + "," + avgBadBins[i] + "\n");
			//System.out.println(i*bucketSize + "-" + (i+1)*bucketSize + "," + bins[i]);
		}
		bout.close();
	}


	// just for debugging to understand the power of jaccard
	private static void displayStats(Map<Citance, List<IndexPair>> predictions) throws IOException {
		int citanceNum=0;
		for (Citance c : predictions.keySet()) {
			// only tmp used for printing jaccard stuff
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			
			System.out.println("\ncitance " + citanceNum++ + ":" + c.citationText);
			System.out.println("\nperfect annotations:");
			
			for (Annotation a : c.annotations) {
				System.out.println("annotator " + a.annotator + ":");
				for (IndexPair ip : a.referenceOffsets) {
					String raw = d.originalText.substring(ip.startPos, ip.endPos);
					//System.out.println("raw: " + raw);
					Set<String> annoTypes = new HashSet<String>();
					String filteredRef = d.filterText(raw);
					StringTokenizer st = new StringTokenizer(filteredRef, " ,.;\"");
					while (st.hasMoreTokens()) {
						String token = st.nextToken();
						annoTypes.add(token);
					}
					double jaccard = 0;
					int intersection = 0;
					for (String token : annoTypes) {
						if (citanceTypes.contains(token)) {
							intersection++;
						}
					}
					// ensures both the citance and reference sentences aren't just stopwords
					if (annoTypes.size() > 0 && citanceTypes.size() > 0 ) {
						jaccard = (double)intersection / ((double)(citanceTypes.size() + annoTypes.size() - intersection));
					}
					System.out.println("\t" + jaccard + "\t" + raw);
				}
			}


			Map<String, Double> sentenceToJaccard = new HashMap<String, Double>();
			Map<String, Double> sentenceToFill = new HashMap<String, Double>();
			
			// look at the Citance's actual returned sentence
			for (int i=0; i<predictions.get(c).size(); i++) {
				
				IndexPair eachSentenceMarkers = predictions.get(c).get(i);
				
				// only tmp used for printing jaccard stuff
				Set<String> refTypes = new HashSet<String>();
				String sent = d.originalText.substring(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos);
				String filteredRef = d.filterText(sent);
				StringTokenizer st = new StringTokenizer(filteredRef, " ,.;\"");
				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					refTypes.add(token);
				}
				double jaccard = 0;
				int intersection = 0;
				for (String token : refTypes) {
					if (citanceTypes.contains(token)) {
						intersection++;
					}
				}
				// ensures both the citance and reference sentences aren't just stopwords
				if (refTypes.size() > 0 && citanceTypes.size() > 0 ) {
					jaccard = (double)intersection / ((double)(citanceTypes.size() + refTypes.size() - intersection));
				}
				
				double avgFill = 0;
				for (Annotation a : c.annotations) {
					double theoreticPercentage = a.theoreticFillInSentence(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos);
					avgFill += theoreticPercentage;
				}
				avgFill /= c.annotations.size();
				
				sentenceToJaccard.put(sent, jaccard);
				sentenceToFill.put(sent, avgFill);
			}
			
			Iterator it = sortByValueDescending(sentenceToJaccard).keySet().iterator();
			int j=0;
			System.out.println("\n\tour top 10 returned sentences (per jaccard sim):");
			while (it.hasNext() && j < 10) {
				String sent = (String)it.next();
				System.out.println("\t" + sentenceToJaccard.get(sent) + "\t" + sentenceToFill.get(sent) + "\t" + sent);
				j++;
			}
			it = sortByValueDescending(sentenceToFill).keySet().iterator();

			j=0;
			System.out.println("\n\tthe actual top 10 sentences (per avg char. overlap w/ golden truth across 4 annotators):");
			while (it.hasNext() && j < 10) {
				String sent = (String)it.next();
				System.out.println("\t" + sentenceToJaccard.get(sent) + "\t" + sentenceToFill.get(sent) + "\t" + sent);
				j++;
			}
		}
	}
	
	// every Citance-Annotator pair gets evaluated and averaged in our recall-type graph
	private static List<Double> scorePredictions(Map<Citance, List<IndexPair>> predictions) throws IOException {
		List<Double> recall = new ArrayList<Double>();
		Map<Integer, List<Double>> recallSums = new HashMap<Integer, List<Double>>();
		BufferedWriter bout = new BufferedWriter(new FileWriter(resultsOut));
		
		// first determine max # of sentences because we want longer docs to still include stats from the shorter docs...
		// so we pad the shorter ones to match the length of the longest
		// (i.e., let 300 be the lengthiest doc; a 200-sentence doc will include its coverage % for sentences 201-300)
		int maxLengthOfDoc = 0;
		for (Citance c : predictions.keySet()) {
			if (predictions.get(c).size() > maxLengthOfDoc) {
				maxLengthOfDoc = predictions.get(c).size();
			}
		}
		
		System.out.println("most sentences in any reference doc: " + maxLengthOfDoc);
		for (Citance c : predictions.keySet()) {
			
			
			//System.out.println("citance: " + c.citationText);
			
			// only tmp used for printing jaccard stuff
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			
			for (int x=0; x<c.annotations.size(); x++) {

				if (method.equals("perfect") && x==0) {
					continue;
				}
				Annotation a = c.annotations.get(x);
				
				double lastFill = 0;
				for (int i=0; i<maxLengthOfDoc; i++) {
					
					// look at the Citance's actual returned sentence
					if (i<predictions.get(c).size()) {
						IndexPair eachSentenceMarkers = predictions.get(c).get(i);
					
						// only tmp used for printing jaccard stuff
						Set<String> refTypes = new HashSet<String>();
						String filteredRef = d.filterText(d.originalText.substring(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos));
						StringTokenizer st = new StringTokenizer(filteredRef, " ,.;\"");
						while (st.hasMoreTokens()) {
							String token = st.nextToken();
							refTypes.add(token);
						}
						double jaccard = 0;
						int intersection = 0;
						for (String token : refTypes) {
							if (citanceTypes.contains(token)) {
								intersection++;
							}
						}
						// ensures both the citance and reference sentences aren't just stopwords
						if (refTypes.size() > 0 && citanceTypes.size() > 0 ) {
							jaccard = (double)intersection / ((double)(citanceTypes.size() + refTypes.size() - intersection));
						}
						double fillPercentage = a.fillInSentence(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos);
						lastFill = fillPercentage;
						if (i < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
							double theoreticPercentage = a.theoreticFillInSentence(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos);
							System.out.println("we have 2 " + i + " " + jaccard + "\t" + theoreticPercentage + ": " + d.originalText.substring(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos));
						} else {
							//System.exit(1);
						}
					}
					List<Double> tmp = new ArrayList<Double>();
					if (recallSums.containsKey(i)) {
						tmp = recallSums.get(i);
					}
					
					// this block is just for debugging
					if (i == maxLengthOfDoc-1 && lastFill < 0.5) {
						System.out.println("lastfill: " + lastFill);
						for (IndexPair ip : a.referenceOffsets) {
							//System.out.println(docs);
							//System.out.println(c.referenceDoc + " has originalText: " + docs.get(c.topicID + ":" + c.referenceDoc).originalText); //.length());
							System.out.println(ip + " => " + c.referenceDoc + " => " + docs.get(c.topicID + ":" + c.referenceDoc).originalText.substring(ip.startPos, ip.endPos));
						}
						System.out.println("our sentence markers:");
						//for (int x=0; x<predictions.get(c).size(); x++) {
							//System.out.println(predictions.get(c).get(x));
						//}
						//break;
						//System.exit(1);
					}
					tmp.add(lastFill);
					recallSums.put(i, tmp);
				}
				
				//System.out.println(c + " => " + recallSums.get(maxLengthOfDoc-1));
			}
			//break;
		}
		
		//int numDocs = recallSums.get(0).size(); // the # of docs that have ranked against 1 sentence
		bout.write("# sentences,avg character recall %\n");
		for (int i=0; i<recallSums.keySet().size(); i++) {
			double sum = 0;
			for (double d : recallSums.get(i)) {
				sum += d;
			}
			recall.add(sum / (double)recallSums.get(i).size()); // calculates the average
			bout.write(i + "," + recall.get(i) + "\n");
			System.out.println(i + "," + recall.get(i));
		}
		bout.close();
		return recall;
	}

	// for each Citance, returns a ranking of the Reference's sentences
	// (NOTE: the target/golden answers could technically be
	// sentence fragments, but this seems rare and would complicate things a lot)
	private static Map<Citance, List<IndexPair>> getLDAPredictions(Map<String, Document> docs, Set<Citance> citances, TopicModelObject tmo) {
		
		// populates the topic data
		System.out.println("docs:" + tmo.docToTopicProbabilities.keySet());
		System.out.println("# topics: " + tmo.topicToWordProbabilities.keySet().size());
		int numDocs = tmo.docToTopicProbabilities.keySet().size();
		int numTopics = tmo.topicToWordProbabilities.keySet().size();
		Map<String, Map<Integer, Double>> wordToTopicProbs = new HashMap<String, Map<Integer, Double>>();
		
		Set<String> malletVocab = new HashSet<String>();
		for (Integer t : tmo.topicToWordProbabilities.keySet()) {
			malletVocab.addAll(tmo.topicToWordProbabilities.get(t).keySet());
		}
		System.out.println("# unique words (aka types):" + malletVocab.size());
		System.out.println("topic vocab: " + malletVocab);
		
		if (malletVocab.contains("randomize")) {
			System.out.println("mallet has randomize!");
		}
		if (badWords.contains("randomize")) {
			System.out.println("badWords has randomize!");
		}
		// calculates P(Z)
		double totalProb = 0;
		double[] p_z = new double[numTopics];
		for (int z=0; z<numTopics; z++) {
			double sum_d_z = 0;
			for (String doc : tmo.docToTopicProbabilities.keySet()) {
				sum_d_z += tmo.docToTopicProbabilities.get(doc)[z];
			}
			p_z[z] = sum_d_z;
			totalProb += sum_d_z;
		}
		double newSum = 0;
		for (int z=0; z<numTopics; z++) {
			p_z[z] /= totalProb;
			System.out.println("P(z=" + z + ") = " + p_z[z]);
			newSum += p_z[z];
		}
		
		// calculates P(Z|W) for each w in vocab:
		for (String w : malletVocab) {
			double denomZ = 0;
			double[] topicProbs = new double[numTopics];
			
			for (int z=0; z<numTopics; z++) {
				double tmp = 0.000001;
				if (tmo.topicToWordProbabilities.get(z).containsKey(w)) {
					tmp += tmo.topicToWordProbabilities.get(z).get(w);
				}
				tmp *= p_z[z]; // now tmp = P(W|Z)P(Z)
				topicProbs[z] = tmp;
				denomZ += tmp;
			}
			Map<Integer, Double> topicToProb = new HashMap<Integer, Double>();
			for (int z=0; z<numTopics; z++) {
				topicProbs[z] /= denomZ;
				topicToProb.put(z, topicProbs[z]);
			}
			wordToTopicProbs.put(w, topicToProb);
		}
		
		Set<String> wordsNotFound = new HashSet<String>();

		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			List<String> citanceWords = removeStopwordsAndBadWords(c.getTextTokensAsList());
			double[] citanceDistribution = getSentenceDistribution(citanceWords, wordToTopicProbs, numTopics);

			for (String w : citanceWords) {
				if (!malletVocab.contains(w)) {
					
					if (w.equals("randomize")) {
						System.out.println("*** citance " + c + " has randomize");
					}
					
					wordsNotFound.add(w);
				}
			}
			

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences.  exiting!");
				System.exit(1);
			}
			for (Sentence s : d.sentences) {
				List<String> curReferenceTypes = removeStopwordsAndBadWords(s.tokens);
				//System.out.println("sentence types:" + curReferenceTypes);
				
				for (String w : curReferenceTypes) {
					if (!malletVocab.contains(w)) {
						wordsNotFound.add(w);
						
						if (w.equals("randomize")) {
							System.out.println("*** ref sentence in doc " + d.name + " has randomize");
						}
						
					}
				}
				double[] referenceSentDistribution = getSentenceDistribution(curReferenceTypes, wordToTopicProbs, numTopics);
				
				double cosineScore = getCosineSim(citanceDistribution, referenceSentDistribution);
				double klScore = getKL(citanceDistribution, referenceSentDistribution);
				
				sentenceScores.put(s, cosineScore);
			}
			
			//System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueDescending(sentenceScores).keySet().iterator();
			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				//System.out.println("score:" + sentenceScores.get(s) + ": " + s.sentence);
			}
			ret.put(c, sentenceMarkers);
			if (sentenceMarkers.size() == 0) {
				System.out.println("we have 0 sentence markers for citance " + c);
			}
		}
		
		System.out.println("# unique words NOT FOUND (aka types):" + wordsNotFound.size() + ": " + wordsNotFound);
		return ret;
	}
	
	private static double getKL(double[] a,double[] b) {
		double ret = 0;
		double padding = 0.0000001;
		int length = a.length;
		for (int i=0; i<length; i++) {
			ret += Math.log((a[i] + padding) / (b[i] + padding))*(a[i] + padding);
		}
		return ret;
	}


	private static double getCosineSim(double[] a, double[] b) {
		int length = a.length;
		double padding = 0.0000001;
		boolean hasAnEmptyVector = true;
		for (int i=0; i<length; i++) {
			if (a[i] > 0) {
				hasAnEmptyVector = false;
			}
		}
		
		if (hasAnEmptyVector) {
			System.out.println("a is empty!");
		}
		hasAnEmptyVector = true;
		for (int i=0; i<length; i++) {
			if (b[i] > 0) {
				hasAnEmptyVector = false;
			}
		}
		if (hasAnEmptyVector) {
			System.out.println("b is empty!");
		}
		
		double num = 0;
		for (int i=0; i<length; i++) {
			num += (a[i]+padding)*(b[i]+padding);
		}
		
		double denomA = 0;
		for (int i=0; i<length; i++) {
			denomA += Math.pow(a[i] + padding, 2);
		}
		
		double denomB = 0;
		for (int i=0; i<length; i++) {
			denomB += Math.pow(b[i] + padding, 2);
		}
		
		return num / (Math.sqrt(denomA) * Math.sqrt(denomB));
	}


	private static double[] getSentenceDistribution(List<String> sentence, Map<String, Map<Integer, Double>> wordToTopicProbs, int numTopics) {
		
		double[] ret = new double[numTopics];

		for (String w : sentence) {
			if (wordToTopicProbs.containsKey(w)) {
				for (Integer t : wordToTopicProbs.get(w).keySet()) {
					ret[t] += wordToTopicProbs.get(w).get(t);
				}
			}
		}
		
		// normalizes
		double total=0;
		for (int t=0; t<numTopics; t++) {
			total += ret[t];
		}
		for (int t=0; t<numTopics; t++) {
			ret[t] /= total;
		}
		return ret;
	}


	// for each Citance, returns a ranking of the Reference's sentences
	// (NOTE: the target/golden answers could technically be
	// sentence fragments, but this seems rare and would complicate things a lot)
	private static Map<Citance, List<IndexPair>> getLongestStringPredictions(Map<String, Document> docs, Set<Citance> citances) {
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			//Set<String> citanceTypes = removeStopwords(c.getTextTokensAsSet());
			List<String> citanceWords = c.getTextTokensAsList();
			
			// just used for efficient look-up to see if any of the reference words are found within the citance
			Set<String> citanceTypes = new HashSet<String>();
			for (String w : citanceWords) {
				citanceTypes.add(w);
			}
			
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);
			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			//System.out.println(c.referenceDoc);
			//System.out.println(docs);
			
			double longestSentenceInDoc = 0;
			for (Sentence s : d.sentences) {
				//Set<String> curReferenceTypes = removeStopwords(s.types);
				List<String> referenceWords = s.tokens;
				
				//System.out.println("sentence types:" + curReferenceTypes);
				double longestLength = 0;
				for (int refIndex=0; refIndex<referenceWords.size(); refIndex++) {
					String token = referenceWords.get(refIndex);
					
					// citance contains the given word, so let's assert that the shared words starts w/ the current token,
					// and see how long it runs... although we don't know where it starts within the Citance, so let's
					// try every occurrence of the token
					if (citanceTypes.contains(token)) {

						for (int i=0; i<citanceWords.size(); i++) {
							// found a starting position to try
							if (citanceWords.get(i).equals(token)) {
								double curLength = 1;
								// stopwords will only count 0.25 of a word (so, 'the dog of' would be 1.5 but 'chester dog' would be 2)
								if (stopwords.contains(token)) {
									curLength = 0.25;
								}
								for (int j=refIndex+1; j<referenceWords.size(); j++) {
									int offset = j-refIndex;
									if ((i+offset) < citanceWords.size() && citanceWords.get(i+offset).equals(referenceWords.get(j))) {
										if (stopwords.contains(referenceWords.get(j))) {
											curLength += 0.25;
										} else {
											curLength++;
										}
									} else {
										break;
									}
								}
								if (curLength > longestLength) {
									longestLength = curLength;
									//System.out.println("ref (" + referenceWords + ") starting at " + refIndex + " (" + referenceWords.get(refIndex) + ") matches with citance @ " + i + " (" + citanceWords.get(i) + ") and has a length of " + curLength);
								}
						
							}
						}
					}
				}
				sentenceScores.put(s, longestLength);
				//System.out.println("sentence " + s + " => " + longestLength);
				
				if (longestLength > longestSentenceInDoc) {
					longestSentenceInDoc = longestLength;
				}
			}
			
			System.out.println("citance:" + c.citationText + " matched " + longestSentenceInDoc + " in the doc");
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
	
	private static Map<Citance, List<IndexPair>> getPerfectPredictions(Map<String, Document> docs, Set<Citance> citances) {
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);
			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			//System.out.println(c.referenceDoc);
			//System.out.println(docs);
			
			Random rand = new Random();
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
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
				
				sentenceScores.put(s, rand.nextDouble());
			}
			
			// adds a randomly chosen perfect Annotation
			Annotation perfect = c.annotations.get(0); //rand.nextInt(c.annotations.size()));
			for (IndexPair ip : perfect.referenceOffsets) {
				sentenceMarkers.add(ip); 
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
	
	
	// for each Citance, returns a ranking of the Reference's sentences
	// (NOTE: the target/golden answers could technically be
	// sentence fragments, but this seems rare and would complicate things a lot)
	private static Map<Citance, List<IndexPair>> getJaccardPredictions(Map<String, Document> docs, Set<Citance> citances) {
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			
			//System.out.println("CITANCE: " + citanceTypes);
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences");
				System.exit(1);
			}
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				//System.out.println("REFERENCE:" + curReferenceTypes);
				double score = 0;
				int intersection = 0;
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
			
			//System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueDescending(sentenceScores).keySet().iterator();
			int tmp=0;

			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				if (tmp < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
					//System.out.println("we have:" + sentenceScores.get(s) + " = " + s.sentence);
				}
				//System.out.println("score:" + sentenceScores.get(s) + ": " + s.sentence);
				tmp++;
			}
			ret.put(c, sentenceMarkers);
			if (sentenceMarkers.size() == 0) {
				System.out.println("we have 0 sentence markers for citance " + c);
			}
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

	private static Set<String> removeStopwordsAndBadWords(Set<String> tokens) {
		Set<String> ret = new HashSet<String>();
		for (String t : tokens) {
			if (!stopwords.contains(t) && !badWords.contains(t)) {
				ret.add(t);
			}
		}
		return ret;
	}

	private static List<String> removeStopwordsAndBadWords(List<String> tokens) {
		List<String> ret = new ArrayList<String>();
		for (String t : tokens) {
			if (!stopwords.contains(t) && !badWords.contains(t)) {
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
			//if (!uidToCitance.containsKey(uid)) {
			uidToCitance.put(uid, c);
			ret.add(c);
			//}
		}
		
		Citance c = uidToCitance.get("D1415_TRAIN:Blasco.txt");
		System.out.println(uidToCitance);
		//System.exit(1);
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
}
