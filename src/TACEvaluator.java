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
import java.util.TreeSet;

import cc.mallet.util.CommandOption.File;

public class TACEvaluator {

	// NOTE: simply change this value
	static String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
	static String docDir = "/Users/christanner/research/projects/TAC2014/TAC_2014_BiomedSumm_Training_Data_V1.2/"; //TAC_2014_BiomedSumm_Training_Data/";
	static boolean runLDA = false;
	
	static int numTriSentences = 0;
	static int numBiSentences = 0;

	// lda, jaccard, jaccardWeighted, jaccardCitanceWeighted, jaccardLength, raw, perfect, longest
	static String method = "jaccardWeighted"; //lda"; 
	
	// 0 = vanilla; non-stopwords = 1 (stopwords = 0 because mallet-tac doens't include them, plus jaccard ignores them anyway)
	// 1 = corpus-wide max_z [p(w|z)]
	// 2 = corpus-wide max_z [p(w|z)p(z)]
	// 3 = corpus-wide max_z [p(w|z)p(z|d)]
	static Integer wordWeightingScheme = 2; // only used if method == jaccardWeighted (jaccardCitanceWeighted uses its own type)
	static double jaccardWeight = .5; // only used for jaccardLength (combining jaccard and sent length)
	
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

	static Map<String, Document> referenceDocs = new HashMap<String, Document>(); // stores just the reference docs
	static Map<String, Document> globalDocs = new HashMap<String, Document>(); // stores both the reference and citance docs
	
	// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
	static String ldaObject = dataDir + "lda_50z_2000i.ser";
	
	static Set<String> badWords = new HashSet<String>();
	static Map<String, Double> wordWeights = new HashMap<String, Double>();
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
	
		stopwords = loadStopwords(stopwordsFile);
		
		makeMalletFileAndGetBadWords(malletInputFile);
		wordWeights = getWordWeights(wordWeightingScheme, ldaObject);
		
		//System.exit(1);
		// create Documents (currently just each Source gets made into a Document, not the reports that cite it)
		referenceDocs = loadReferenceDocuments(annoInputFile);

		Set<Citance> citances = loadCitances(annoInputFile);
		
		/*
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
			}
		}
		System.out.println("most off: " + mostOff);
		System.out.println("good anno: " + goodAnno);
		System.out.println("bad anno: " + badAnno);
		System.exit(1);
		*/
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
			
			predictions = getLDAPredictions(referenceDocs, citances, tmo);
		} else if (method.equals("jaccard")) {
			predictions = getJaccardPredictions(referenceDocs, citances);
		} else if (method.equals("jaccardWeighted")) {
			predictions = getJaccardWeightedPredictions(referenceDocs, citances, ldaObject);
		} else if (method.equals("jaccardCitanceWeighted")) {
			predictions = getJaccardCitanceWeightedPredictions(referenceDocs, citances, ldaObject);
		} else if (method.equals("jaccardLength")) {
			predictions = getJaccardAndLengthPredictions(referenceDocs, citances, jaccardWeight);
		} else if (method.equals("raw")) {
			predictions = getRawPredictions(referenceDocs, citances);
		} else if (method.equals("longest")) {
			predictions = getLongestStringPredictions(referenceDocs, citances);
		} else if (method.equals("perfect")) {
			predictions = getPerfectPredictions(referenceDocs, citances);
		}
		
		List<Double> recall = scorePredictions(predictions);
		//displayStats(predictions);
		//printSimilarityStats(predictions, 50);
		//printSentenceImportance(citances); // prints the sentencePlacement graphs to results.csv
	}


	// sets the word weights for every word that's found within the topic model
	// 0 = vanilla; non-stopwords = 1 (stopwords = 0 because mallet-tac doens't include them, plus jaccard ignores them anyway)
	// 1 = corpus-wide max_z [p(w|z)]
	// 2 = corpus-wide max_z [p(w|z)p(z)]
	// 3 = corpus-wide max_z [p(w|z)p(z|d)]
	private static Map<String, Double> getWordWeights(Integer wordWeightingScheme, String ldaObject) throws IOException, ClassNotFoundException {
		Map<String, Double> wordWeights = new HashMap<String, Double>();
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(ldaObject));
		TopicModelObject tmo = (TopicModelObject)in.readObject();
		
		Set<String> malletVocab = new HashSet<String>();
		for (Integer t : tmo.topicToWordProbabilities.keySet()) {
			malletVocab.addAll(tmo.topicToWordProbabilities.get(t).keySet());
		}
		
		System.out.println("# unique words (aka types):" + malletVocab.size());
		// calculates P(Z)
		int numTopics = tmo.topicToWordProbabilities.keySet().size();
		double totalProb = 0;
		double[] p_z = new double[numTopics];
		for (int z = 0; z < numTopics; z++) {
			double sum_d_z = 0;
			for (String doc : tmo.docToTopicProbabilities.keySet()) {
				sum_d_z += tmo.docToTopicProbabilities.get(doc)[z];
			}
			p_z[z] = sum_d_z;
			totalProb += sum_d_z;
		}
		for (int z = 0; z < numTopics; z++) {
			p_z[z] /= totalProb;
		}
		
		if (wordWeightingScheme == 0) {
			for (String w : malletVocab) {
				wordWeights.put(w, (double) 1.0);
			}
		} else if (wordWeightingScheme == 1) {
			for (String w : malletVocab) {
				double maxTopicProb = 0;
				for (int z = 0; z < numTopics; z++) {
					if (tmo.topicToWordProbabilities.get(z).containsKey(w)) {
						if (tmo.topicToWordProbabilities.get(z).get(w) > maxTopicProb) {
							maxTopicProb = tmo.topicToWordProbabilities.get(z).get(w);
						}
					}
				}
				wordWeights.put(w, maxTopicProb);
			}
		} else if (wordWeightingScheme == 2) {
			double max = 0;
			for (String w : malletVocab) {
				double maxTopicProb = 0;
				for (int z = 0; z < numTopics; z++) {
					if (tmo.topicToWordProbabilities.get(z).containsKey(w)) {
						if (tmo.topicToWordProbabilities.get(z).get(w)*p_z[z] > maxTopicProb) {
							maxTopicProb = tmo.topicToWordProbabilities.get(z).get(w)*p_z[z];
						}
					}
				}
				if (maxTopicProb > max) {
					max = maxTopicProb;
				}
				wordWeights.put(w, maxTopicProb);
			}
			
			// normalizes so the most important word = 1
			for (String w : malletVocab) {
				wordWeights.put(w, wordWeights.get(w)/max);
			}
		}
		return wordWeights;
	}


	private static void makeMalletFileAndGetBadWords(String malletOutput) throws IOException {
		//Map<String, Document> ret = new HashMap<String, Document>();
		

		BufferedReader bin = new BufferedReader(new FileReader(annoInputFile));
		String curLine = "";
		globalDocs = new HashMap<String, Document>();
		
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
					if (!globalDocs.containsKey(topicID + ":" + doc)) {
						Document d = new Document(docDir, topicID, doc);
						globalDocs.put(topicID + ":" + doc, d);
					}
					//System.out.println("source: " + sourceName);
				} else if (field.startsWith("Citing Article:")) {
					StringTokenizer st2 = new StringTokenizer(field);
					st2.nextToken();
					st2.nextToken();
					doc = st2.nextToken();
					if (!globalDocs.containsKey(topicID + ":" + doc)) {
						Document d = new Document(docDir, topicID, doc);
						globalDocs.put(topicID + ":" + doc, d);
					}
					//System.out.println("CITING ARTICLE!: " + doc);
				}
			}
		}
		
		// goes through all docs (citing and reference docs) and writes to the mallet output file
		BufferedWriter bout = new BufferedWriter(new FileWriter(malletOutput));
		
		Map<String, Set<String>> wordToDocs = new HashMap<String, Set<String>>();
		
		// goes through once to first find all bad words
		for (String doc : globalDocs.keySet()) {
			Set<String> docTypes = new HashSet<String>();
			
			Document d = globalDocs.get(doc);
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
			double percent = (double)numDocs / (double)globalDocs.keySet().size();
			if (numDocs < minNumDocs || percent > maxPercentDocs || w.length() == 1) {
				badWords.add(w);
			}
		}
		System.out.println(badWords.size() + " of " + wordToDocs.keySet().size() + " (" + (double)badWords.size()/(double)wordToDocs.keySet().size()  +") words were bad: " + badWords);
		
		// goes through again and writes the mallet file for the good words
		for (String doc : globalDocs.keySet()) {

			Document d = globalDocs.get(doc);
			bout.write(d.topicID + ":" + d.name + " " + d.topicID + ":" + d.name);
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




	/*
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
	*/
	
	
	// prints how important each Sentence is wrt its placement within its Doc
	private static void printSentenceImportance(Set<Citance> citances) throws IOException {
		double[] ret = new double[700];
		double[] sectionLengths = new double[300];
		int retSum = 0;
		int sSum = 0;
		for (Citance c : citances) {
			// only tmp used for printing jaccard stuff
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			Document d = referenceDocs.get(c.topicID + ":" + c.referenceDoc);
			
			//System.out.println("\ncitance:" + c.citationText);
			//System.out.println("\nperfect annotations:");
			
			int lastMarker = 0;
			for (int i=0; i<d.sectionMarkers.size(); i++) {
				int curMarker = d.sectionMarkers.get(i);
				if (curMarker != lastMarker) {
					sectionLengths[(curMarker - lastMarker)]++;
					sSum++;
				}
				lastMarker = curMarker;
			}
			
			for (int i=0; i<d.sentences.size(); i++) {
				double prec = d.sentences.get(i).getPrecision(c.annotations);
				
				// finds the # of sentences this is within the current section
				int lastParagraph = 0;
				for (int j=0; j<d.sectionMarkers.size(); j++) {
					lastParagraph = d.sectionMarkers.get(j);
					if (lastParagraph >= i) {
						break;
					}
				}
				int sentNum = lastParagraph - i;
				ret[sentNum] += prec;
				retSum += prec;
				//System.out.println(i + " = " + prec);
			}
			//System.exit(1);
		}
		BufferedWriter bout = new BufferedWriter(new FileWriter(resultsOut));
		bout.write("sentence#,%paragraphsOfThisLength,%goldenSentencesAtThisPosition\n");
		for (int i=0; i<100; i++) {
			bout.write(i + "," + (double)sectionLengths[i]/(double)sSum + "," + (double)ret[i]/retSum + "\n");
		}
		bout.close();
	}
	
	/*
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
	*/
	
	// every Citance-Annotator pair gets evaluated and averaged in our recall-type graph
	private static List<Double> scorePredictions(Map<Citance, List<IndexPair>> predictions) throws IOException {
		List<Double> f1 = new ArrayList<Double>();
		Map<Integer, List<Double>> f1Sums = new HashMap<Integer, List<Double>>();
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
			Document d = referenceDocs.get(c.topicID + ":" + c.referenceDoc);
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			d.clearBytes();
			
			double lastF1 = 0;
			for (int i=0; i<maxLengthOfDoc; i++) {
				
				// look at the Citance's actual returned sentence
				if (i<predictions.get(c).size()) {
					IndexPair eachSentenceMarkers = predictions.get(c).get(i);
					
					d.fillBytes(eachSentenceMarkers);
					
					// debugging; prints the last sentence's fill %
					/*
					if (i==predictions.get(c).size()-1) {
						System.out.println("last sentence; " + d.bytesSpanned.size() + " out of " + d.originalText.length() + " = " + (double)d.bytesSpanned.size() / (double)d.originalText.length());
						Set<Integer> goldenBytes = new HashSet<Integer>();
						TreeSet<Integer> missingBytes = new TreeSet<Integer>();
						for (int eachAnno=0; eachAnno<c.annotations.size(); eachAnno++) {

							for (IndexPair ip : c.annotations.get(eachAnno).referenceOffsets) {
								for (int bI=ip.startPos; bI<=ip.endPos; bI++) {
									goldenBytes.add(bI);
								}
							}
							// skips averaging with Annotator #1
							
							if (method.equals("perfect") && eachAnno==0) {
								continue;
							}
							
						}
						for (Integer goldenByte : goldenBytes) {
							if (!d.bytesSpanned.contains(goldenByte)) { 
								missingBytes.add(goldenByte);
							}
						}
						System.out.println("\tannotators marked up total of " + goldenBytes.size() + " bytes; we missed " + missingBytes.size() + " = " + (double)missingBytes.size() / (double)goldenBytes.size());
						System.out.println("\tmissing: " + missingBytes);
						System.out.println("\tfrom doc: " + d.name);
						for (Integer missed : missingBytes) {
							System.out.println("\t" + missed + ":" + d.originalText.charAt(missed));
						}
					}	
					*/
					List<Annotation> annos = new ArrayList<Annotation>(c.annotations);
					if (method.equals("perfect")) {
						annos.remove(0);
					}
					double weightedF1 = d.calculateWeightedF1(annos);
					lastF1 = weightedF1;
				}
				
				
				List<Double> tmp = new ArrayList<Double>();
				if (f1Sums.containsKey(i)) {
					tmp = f1Sums.get(i);
				}
				tmp.add(lastF1);
				f1Sums.put(i, tmp);
			}
		}
		bout.write("# sentences,avg character weighted F1 %\n");
		for (int i=0; i<f1Sums.keySet().size(); i++) {
			double sum = 0;
			for (double d : f1Sums.get(i)) {
				sum += d;
			}
			f1.add(sum / (double)f1Sums.get(i).size()); // calculates the average
			bout.write(i + "," + f1.get(i) + "\n");
			System.out.println(i + "," + f1.get(i));
		}
		bout.close();
		return f1;
		/*
			
			for (int x=0; x<c.annotations.size(); x++) {

				// skips averaging with Annotator #1
				if (method.equals("perfect") && x==0) {
					continue;
				}
				Annotation a = c.annotations.get(x);
				
				double lastF1 = 0;
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
		*/
		

		
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
					}
				}
				double[] referenceSentDistribution = getSentenceDistribution(curReferenceTypes, wordToTopicProbs, numTopics);
				
				double cosineScore = getCosineSim(citanceDistribution, referenceSentDistribution);
				double klScore = getKL(citanceDistribution, referenceSentDistribution);
				
				sentenceScores.put(s, 1-klScore);
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
			
			// adds annotator #1's perfect Annotation (we always pick #1 because we need to keep track of
			// which OTHER guys to eval against; so we'll just always say #1 was chosen)
			Annotation perfect = c.annotations.get(0);
			for (IndexPair ip : perfect.referenceOffsets) {
				//sentenceMarkers.add(ip);  TODO: DONT LEAVE THIS COMMENTED OUT; it forces all choices to be random!
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
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);
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
				
				/*
				if (tmp < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
					//System.out.println("we have:" + sentenceScores.get(s) + " = " + s.sentence);
				}
				*/
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

	private static Map<Citance, List<IndexPair>> getJaccardAndLengthPredictions(Map<String, Document> docs, Set<Citance> citances, double jaccardWeight) {
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			Map<Sentence, Double> jaccardScores = new HashMap<Sentence, Double>();
			Map<Sentence, Double> lengthScores = new HashMap<Sentence, Double>();
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
			
			// sets the lengthScores
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);
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
					score = curReferenceTokens.size(); //(double)intersection / ((double)(citanceTypes.size() + curReferenceTypes.size() - intersection));
				}
				lengthScores.put(s, score);
			}
			
			// sets the jaccard Scores
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);

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
				jaccardScores.put(s, score);
			}
			
			for (Sentence s : d.sentences) {
				Iterator it3 = sortByValueDescending(jaccardScores).keySet().iterator();
				
				int jaccardPos = 0;
				int lengthPos = 0;
				int indexPos = 0;
				while (it3.hasNext()) {
					Sentence s2 = (Sentence)it3.next();
					if (s2 == s) {
						jaccardPos = indexPos;
						break;
					}
					indexPos++;
				}
				it3 = sortByValueDescending(lengthScores).keySet().iterator();
				indexPos = 0;
				while (it3.hasNext()) {
					Sentence s2 = (Sentence)it3.next();
					if (s2 == s) {
						lengthPos = indexPos;
						break;
					}
					indexPos++;
				}
				//System.out.println("found sent at " + jaccardPos + " and " + lengthPos);
				sentenceScores.put(s, jaccardWeight * jaccardPos + (1-jaccardWeight)*lengthPos);
			}
			//System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueAscending(sentenceScores).keySet().iterator();
			int tmp=0;

			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				/*
				if (tmp < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
					//System.out.println("we have:" + sentenceScores.get(s) + " = " + s.sentence);
				}
				*/
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
	
	private static Map<Citance, List<IndexPair>> getJaccardCitanceWeightedPredictions(Map<String, Document> docs, Set<Citance> citances, String ldaObject) throws FileNotFoundException, IOException, ClassNotFoundException {
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(ldaObject));
		TopicModelObject tmo = (TopicModelObject)in.readObject();
		int numTopics = tmo.topicToWordProbabilities.keySet().size();
		Set<String> malletVocab = new HashSet<String>();
		for (Integer t : tmo.topicToWordProbabilities.keySet()) {
			malletVocab.addAll(tmo.topicToWordProbabilities.get(t).keySet());
		}
		
		for (String doc : tmo.docToTopicProbabilities.keySet()) {
			System.out.println("lda doc: " + doc);
		}

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
			
			// gets the words contained within the Citance doc
			Document citanceDoc = globalDocs.get(c.topicID + ":" + c.citingDoc);
			Set<String> citanceWords = new HashSet<String>();
			for (Sentence s : citanceDoc.sentences) {
				citanceWords.addAll(s.types);
			}
			System.out.println("citance doc: " + citanceDoc.name + " has " + citanceWords.size() + " unique words");
			// gets the word weights for the citance's document
			Map<String, Double> wordWeights = new HashMap<String, Double>();
			double sum = 0;
			double max = 0;
			for (String w : citanceTypes) { //citanceWords) {
				
				// marginalizes over all topics
				// P(w|d) = sum_z [p(w|z)p(z|d)]
				double currentWordProb = 0.00001;
				for (int z=0; z<numTopics; z++) {
					if (tmo.topicToWordProbabilities.get(z).containsKey(w)) {
						currentWordProb += tmo.topicToWordProbabilities.get(z).get(w)*tmo.docToTopicProbabilities.get(c.topicID + ":" + c.citingDoc)[z];
					}
				}
				if (currentWordProb > max) {
					max = currentWordProb;
				}
				sum += currentWordProb;
				wordWeights.put(w,currentWordProb);
			}
			// normalizes over all wordProbs
			for (String w : citanceTypes) {
				wordWeights.put(w, wordWeights.get(w)/max); ///sum);
			}
			
			Iterator it2 = sortByValueDescending(wordWeights).keySet().iterator();
			while (it2.hasNext()) {
				String w = (String)it2.next();
				System.out.println("word " + w + " has weight: " + wordWeights.get(w));
			}
			//System.exit(1);
			int sentNum = 1;
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);
				
				//System.out.println("REFERENCE:" + curReferenceTypes);
				double score = 0;
				int intersection = 0;
				for (String token : curReferenceTokens) {
					if (citanceTypes.contains(token) && wordWeights.containsKey(token)) {
						intersection += (wordWeights.get(token));
						//System.out.println("\tshared: " + token + " = " + wordWeights.get(token));
					} else if (citanceTypes.contains(token) && !wordWeights.containsKey(token)) {
						System.err.println("we dont have word weight for "+ token);
					}
				}
				
				Set<String> union = new HashSet<String>(citanceTypes);
				union.addAll(curReferenceTypes);
				double denom = 0; //.000001;
				for (String w : union) {
					if (curReferenceTypes.contains(w) && citanceTypes.contains(w)) {
						continue;
					}
					if (wordWeights.containsKey(w)) {
						denom += wordWeights.get(w);
					}
				}
				
				// ensures both the citance and reference sentences aren't just stopwords
				if (curReferenceTypes.size() > 0 && citanceTypes.size() > 0 ) {
					score = (double)intersection; // / (double)denom;
				}
				//System.out.println("inter: " + intersection + "; denom: " + denom);
				if (sentNum > 20 && sentNum < 50) {
					sentenceScores.put(s, 1.0);
				} else {
					sentenceScores.put(s, 1.0 / (double)sentNum);//score); TODO DONT LEAVE THIS!
				
				}
				sentNum++;
			}

			//System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueDescending(sentenceScores).keySet().iterator();
			int tmp=0;

			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				/*
				if (tmp < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
					//System.out.println("we have:" + sentenceScores.get(s) + " = " + s.sentence);
				}
				*/
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
	
	private static Map<Citance, List<IndexPair>> getJaccardWeightedPredictions(Map<String, Document> docs, Set<Citance> citances, String ldaObject) throws FileNotFoundException, IOException, ClassNotFoundException {
		
		// displays wordWeights
		/*
		Iterator it2 = sortByValueDescending(wordWeights).keySet().iterator();
		int numWords = 0;
		while (it2.hasNext() && numWords < 200) {
			String w = (String)it2.next();
			System.out.println(w + " = " + wordWeights.get(w));
			numWords++;
		}
		*/
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(ldaObject));
		TopicModelObject tmo = (TopicModelObject)in.readObject();
		int numTopics = tmo.topicToWordProbabilities.keySet().size();
		double totalProb = 0;
		double[] p_z = new double[numTopics];
		for (int z = 0; z < numTopics; z++) {
			double sum_d_z = 0;
			for (String doc : tmo.docToTopicProbabilities.keySet()) {
				sum_d_z += tmo.docToTopicProbabilities.get(doc)[z];
			}
			p_z[z] = sum_d_z;
			totalProb += sum_d_z;
		}
		for (int z = 0; z < numTopics; z++) {
			p_z[z] /= totalProb;
		}
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Set<Integer> sentencesAdded = new HashSet<Integer>();
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			Map<Integer, Double> triSentenceScores = new HashMap<Integer, Double>();
			Map<Integer, Double> biSentenceScores = new HashMap<Integer, Double>();
			Map<Integer, Double> uniSentenceScores = new HashMap<Integer, Double>();
			
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			Document citingDoc = globalDocs.get(c.topicID + ":" + c.citingDoc);
			
			// gets the Set of all words in the Citing DOC
			Set<String> citingTypes = new HashSet<String>();
			for (Sentence s : citingDoc.sentences) {
				citingTypes.addAll(s.types);
			}
			System.out.println("citing doc:" + citingDoc.name + " contains " + citingTypes.size() + " unique types! " + citingDoc.sentences.size());
			if (citingTypes.size() == 0) {
				System.err.println("citing doc contains 0 types?!");
			}
			Set<String> citingDocTypes = removeStopwordsAndBadWords(citingTypes);
			
			// the non-stoplist types from the Citance
			Set<String> citanceTypes = removeStopwordsAndBadWords(c.getTextTokensAsSet());
			
			List<String> citanceTokens = removeStopwordsAndBadWords(c.getTextTokensAsList());
			
			// determines the most popular TOPIC amongst Citance
			int bestTopic = 0;
			double mostLikelyProb = -9999999;
			for (int z=0; z<numTopics; z++) {
				double currentWordProb = 0;
				for (String w : citanceTypes) { // also try TYPES
					if (tmo.topicToWordProbabilities.get(z).containsKey(w)) {
						currentWordProb += Math.log(tmo.topicToWordProbabilities.get(z).get(w));
					} else {
						System.out.println("we dont have:" + w);
					}
				}
				if (currentWordProb > mostLikelyProb) {
					mostLikelyProb = currentWordProb;
					bestTopic = z;
				}
			}
			System.out.println("citance's best topic:" + bestTopic + ":" + mostLikelyProb);
			
			//System.out.println("CITANCE: " + citanceTypes);
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences");
				System.exit(1);
			}
			
			// calculates triSentence scores
			for (int i=0; i<d.sentences.size()-2; i++) {
				Sentence s = d.sentences.get(i);
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				
				s = d.sentences.get(i+1);
				curReferenceTypes.addAll(removeStopwordsAndBadWords(s.types));
				
				s = d.sentences.get(i+2);
				curReferenceTypes.addAll(removeStopwordsAndBadWords(s.types));
				
				int intersection = 0;
				for (String token : curReferenceTypes) {
					if (citanceTypes.contains(token)) {
						if (wordWeights.containsKey(token)) {
							intersection += 1;
						}
					}
				}
				triSentenceScores.put(i, (double)intersection);
			}
		
			// calculates biSentence scores
			for (int i=0; i<d.sentences.size()-1; i++) {
				Sentence s = d.sentences.get(i);
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				
				s = d.sentences.get(i+1);
				curReferenceTypes.addAll(removeStopwordsAndBadWords(s.types));
				
				int intersection = 0;
				for (String token : curReferenceTypes) {
					if (citanceTypes.contains(token)) {
						if (wordWeights.containsKey(token)) {
							intersection += 1;
						}
					}
				}
				biSentenceScores.put(i, (double)intersection);
			}
			
			//for (Sentence s : d.sentences) {
			for (int i=0; i<d.sentences.size(); i++) {
				
				Sentence s = d.sentences.get(i);
				
				Set<String> curReferenceTypes = removeStopwordsAndBadWords(s.types);
				//System.out.println("REFERENCE:" + curReferenceTypes);
				List<String> curReferenceTokens = removeStopwordsAndBadWords(s.tokens);
				double curProb = 0;
				for (String w : curReferenceTypes) {
					if (tmo.topicToWordProbabilities.get(bestTopic).containsKey(w)) {
						curProb += Math.log(tmo.topicToWordProbabilities.get(bestTopic).get(w)); //*p_z[bestTopic]);
					}
				}
				curProb /= curReferenceTypes.size();
				
				// regular VanillaSum
				double score = 0;
				int intersection = 0;
				for (String token : curReferenceTypes) {
					if (citanceTypes.contains(token)) {
						if (wordWeights.containsKey(token)) {
							intersection += 1; //(wordWeights.get(token));
							//System.out.println(token + " adds " + wordWeights.get(token));
						}
					}
				}
				
				Set<String> union = new HashSet<String>(citanceTypes);
				union.addAll(curReferenceTypes);
				double denom = 0; //0.000001;
				for (String w : union) {
					if (curReferenceTypes.contains(w) && citanceTypes.contains(w)) {
						continue;
					}
					if (wordWeights.containsKey(w)) {
						denom += 1; //wordWeights.get(w);
					}
				}
				
				// ensures both the citance and reference sentences aren't just stopwords
				if (curReferenceTypes.size() > 0 && citanceTypes.size() > 0 ) {
					score = (double)intersection; // / (double)denom;
				}
				uniSentenceScores.put(i, score);
			}
			//System.exit(1);
			//System.out.println("citance:" + c.citationText);
			
			// adds trisentences
			int tmp=0;
			Iterator it = sortByValueDescending(triSentenceScores).keySet().iterator();
			while (it.hasNext() && tmp<numTriSentences) {
				
				int sentenceStart = (Integer)it.next();
				
				if (!sentencesAdded.contains(sentenceStart) && !sentencesAdded.contains(sentenceStart+1) && !sentencesAdded.contains(sentenceStart+2)) {
					int startPos = d.sentences.get(sentenceStart).startPos;
					int endPos = d.sentences.get(sentenceStart+2).endPos;
					IndexPair i = new IndexPair(startPos, endPos);
					System.out.println("adding tri:" + i);
					sentencesAdded.add(sentenceStart);
					sentencesAdded.add(sentenceStart+1);
					sentencesAdded.add(sentenceStart+2);
					sentenceMarkers.add(i);
					tmp++;	
				}

			}

			// adds biSentences
			it = sortByValueDescending(biSentenceScores).keySet().iterator();
			tmp = 0;
			while (it.hasNext() && tmp<numBiSentences) {
				int sentenceStart = (Integer)it.next();
				
				if (!sentencesAdded.contains(sentenceStart) && !sentencesAdded.contains(sentenceStart+1)) {
					int startPos = d.sentences.get(sentenceStart).startPos;
					int endPos = d.sentences.get(sentenceStart+1).endPos;
					IndexPair i = new IndexPair(startPos, endPos);
					System.out.println("adding bi:" + i);
					sentencesAdded.add(sentenceStart);
					sentencesAdded.add(sentenceStart+1);
					sentenceMarkers.add(i);
					tmp++;					
				}
			}

			// original uniSentences
			it = sortByValueDescending(uniSentenceScores).keySet().iterator();
			tmp = 0;
			while (it.hasNext()) {
				
				int sentenceStart = (Integer)it.next();
				
				if (!sentencesAdded.contains(sentenceStart)) {
					Sentence s = d.sentences.get(sentenceStart);	
					IndexPair i = new IndexPair(s.startPos, s.endPos);
					if (tmp < 10) {
						System.out.println("adding uni:" + i);
					}
					sentenceMarkers.add(i);
					tmp++;					
				}
			}
			
			/*
			// alternatively, let's always add sentences after and before, too: and no repeats
			Set<Sentence> alreadyAdded = new HashSet<Sentence>();
			while (it.hasNext()) {
				// attempts to add current node
				Sentence s = (Sentence)it.next();
				if (!alreadyAdded.contains(s)) {
					IndexPair i = new IndexPair(s.startPos, s.endPos);
					sentenceMarkers.add(i);
					alreadyAdded.add(s);
				}

				int sIndex = d.sentences.indexOf(s);
				
				// attempts to add Sentence after it
				if (d.sentences.size() > (sIndex+1)) {
					Sentence s2 = d.sentences.get(sIndex+1);
					if (!alreadyAdded.contains(s2)) {
						IndexPair i = new IndexPair(s2.startPos, s2.endPos);
						sentenceMarkers.add(i);
						alreadyAdded.add(s2);
					}
				}
				
				// attempts to add Sentence before it
				if (sIndex > 0) {
					Sentence s2 = d.sentences.get(sIndex-1);
					if (!alreadyAdded.contains(s2)) {
						IndexPair i = new IndexPair(s2.startPos, s2.endPos);
						sentenceMarkers.add(i);
						alreadyAdded.add(s2);
					}
				}
				
			}
			*/
			ret.put(c, sentenceMarkers);
			if (sentenceMarkers.size() == 0) {
				System.out.println("we have 0 sentence markers for citance " + c);
			}
		}
		return ret;
	}
	
	// works w/ the raw sentence (stopwords and 'bad' words included)
	// we will try jaccard sliding window and BLEU attempts here
	private static Map<Citance, List<IndexPair>> getRawPredictions(Map<String, Document> docs, Set<Citance> citances) {
		
		// displays wordWeights
		Iterator it2 = sortByValueDescending(wordWeights).keySet().iterator();
		int numWords = 0;
		while (it2.hasNext() && numWords < 200) {
			String w = (String)it2.next();
			System.out.println(w + " = " + wordWeights.get(w));
			numWords++;
		}
		
		Map<Citance, List<IndexPair>> ret = new HashMap<Citance, List<IndexPair>>();
		
		for (Citance c : citances) {
			
			Map<Sentence, Double> sentenceScores = new HashMap<Sentence, Double>();
			List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
			
			// the non-stoplist types from the Citance
			List<String> citanceTokens = c.getTextTokensAsList();
			
			System.out.println("CITANCE: " + citanceTokens);
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			System.out.println("reference doc: " + d.name);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences");
				System.exit(1);
			}
			double bestSentScore = 0;
			Sentence bestSent = null;
			for (Sentence s : d.sentences) {
				List<String> curReferenceTokens = s.tokens;
				
				//System.out.println("REFERENCE:" + curReferenceTokens);
				double score = 0;
				int intersection = 0;
				
				int windowSize = 80;
				int bestScore = 0;
				// sliding window of size 7
				for (int startI=0; startI<=curReferenceTokens.size()-windowSize; startI++) {
					
					int currentWindowScore = 0;
					for (int index=startI; index<windowSize; index++) {
						if (citanceTokens.contains(curReferenceTokens.get(index))) {
							currentWindowScore++;
						}
					}
					if (currentWindowScore > bestScore) {
						bestScore = currentWindowScore;
					}
				}
				
				for (String token : curReferenceTokens) {
					if (citanceTokens.contains(token)) {
						if (wordWeights.containsKey(token)) {
							intersection += (wordWeights.get(token));
							//System.out.println(token + " adds " + wordWeights.get(token));
						}
					}
				}
				
				Set<String> union = new HashSet<String>(citanceTokens);
				union.addAll(curReferenceTokens);
				double denom = 0; //0.000001;
				for (String w : union) {
					if (curReferenceTokens.contains(w) && citanceTokens.contains(w)) {
						continue;
					}
					if (wordWeights.containsKey(w)) {
						denom += 1; //wordWeights.get(w);
					}
				}
				//System.out.println("union: " + union + " = " + denom);
				
				// ensures both the citance and reference sentences aren't just stopwords
				if (curReferenceTokens.size() > 0 && citanceTokens.size() > 0 ) {
					score = (double)intersection; // / (double)denom;
				}
				sentenceScores.put(s, (double)bestScore);
				
				if (bestScore > bestSentScore) {
					bestSentScore = bestScore;
					bestSent = s;
				}
			}
			System.out.println("best sent (" + bestSentScore + "): " + bestSent);
			//System.exit(1);
			//System.out.println("citance:" + c.citationText);
			Iterator it = sortByValueDescending(sentenceScores).keySet().iterator();
			int tmp=0;

			while (it.hasNext()) {
				Sentence s = (Sentence)it.next();
				IndexPair i = new IndexPair(s.startPos, s.endPos);
				sentenceMarkers.add(i);
				
				/*
				if (tmp < 10 && c.citationText.startsWith("In a recent issue of Cell, the Downward laboratory  went all the way from identifying GATA2 as a novel synthetic lethal gene to validating it using Kras-driven GEM models and, finally,")) {
					//System.out.println("we have:" + sentenceScores.get(s) + " = " + s.sentence);
				}
				*/
				//System.out.println("score:" + sentenceScores.get(s) + ": " + s.sentence);
				tmp++;
			}
			ret.put(c, sentenceMarkers);
			if (sentenceMarkers.size() == 0) {
				System.out.println("we have 0 sentence markers for citance " + c);
			}
			//System.exit(1);
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

	@SuppressWarnings("unchecked")
	static Map sortByValueAscending(Map map) {
		List list = new LinkedList(map.entrySet());
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
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
			
			String uid = topicID + ":" + Integer.toString(citanceNum);
			Citance c = null;
			if (uidToCitance.containsKey(uid)) {
				c = uidToCitance.get(uid);
			} else {
				c = new Citance(topicID, citanceNum, referenceDoc, citingDoc, citationText);
			}

			Annotation a = new Annotation(refOffsets, refText, discourse, annotator);
			//System.out.println(a);
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
