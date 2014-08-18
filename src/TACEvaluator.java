import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;


public class TACEvaluator {

	// NOTE: simply change this value
	static String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
	static String docDir = "/Users/christanner/research/projects/TAC2014/TAC_2014_BiomedSumm_Training_Data/";
	static boolean runLDA = false;
	
	// LDA's input files
	static String annoInputFile = dataDir + "annoLegend.txt";
	static String malletInputFile = dataDir + "mallet-tac.txt";
	static String stopwords = dataDir + "stopwords.txt";

	// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
	static String ldaObject = dataDir + "lda_2000i.ser";
	
	public static void main(String[] args) throws IOException {
	
		// create Documents (currently just each Source gets made into a Document, not the reports that cite it)
		Map<String, Document> docs = loadDocuments(annoInputFile);
		Set<Citance> citances = loadCitances(annoInputFile);
		
		// NOTE: LDA variables/params are in the LDA's class as global vars
		if (runLDA) {
			LDA l = new LDA(malletInputFile, stopwords);
			l.runLDA();
			l.saveLDA(ldaObject);
		}
	}

	
	static Set<Citance> loadCitances(String annoInputFile2) {
		Set<Citance> ret = new HashSet<Citance>();
		
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


	static Map<String, Document> loadDocuments(String annoInputFile) throws IOException {
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
}
