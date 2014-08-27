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

public class TACEvaluator {

	// NOTE: simply change this value
	static String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
	static String docDir = "/Users/christanner/research/projects/TAC2014/TAC_2014_BiomedSumm_Training_Data_V1.2/"; //TAC_2014_BiomedSumm_Training_Data/";
	static boolean runLDA = false;
	static String method = "lda"; //lda";

	
	static Set<String> stopwords;
	
	// output
	static String resultsOut = dataDir + "results.csv";
	
	// LDA's input files
	static String annoInputFile = dataDir + "annoLegend.txt";
	static String malletInputFile = dataDir + "mallet-tac.txt";
	static String stopwordsFile = dataDir + "stopwords.txt";

	static Map<String, Document> docs = new HashMap<String, Document>();
	
	// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
	static String ldaObject = dataDir + "lda_50z_2000i.ser";
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
	
		stopwords = loadStopwords(stopwordsFile);
		
		// create Documents (currently just each Source gets made into a Document, not the reports that cite it)
		docs = loadReferenceDocuments(annoInputFile);
		Set<Citance> citances = loadCitances(annoInputFile);
		
		// NOTE: LDA variables/params are in the LDA's class as global vars
		if (runLDA) {
			LDA l = new LDA(malletInputFile, stopwordsFile);
			l.runLDA();
			l.saveLDA(ldaObject);
		}
		
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
			for (Annotation a : c.annotations) {
				
				double lastFill = 0;
				for (int i=0; i<maxLengthOfDoc; i++) {
					
					// look at the Citance's actual returned sentence
					if (i<predictions.get(c).size()) {
						IndexPair eachSentenceMarkers = predictions.get(c).get(i);
					
						double fillPercentage = a.fillInSentence(eachSentenceMarkers.startPos, eachSentenceMarkers.endPos);
						lastFill = fillPercentage;
					}
					List<Double> tmp = new ArrayList<Double>();
					if (recallSums.containsKey(i)) {
						tmp = recallSums.get(i);
					}
					if (i == maxLengthOfDoc-1 && lastFill < 0.5) {
						System.out.println("lastfill: " + lastFill);
						for (IndexPair ip : a.referenceOffsets) {
							//System.out.println(docs);
							//System.out.println(c.referenceDoc + " has originalText: " + docs.get(c.topicID + ":" + c.referenceDoc).originalText); //.length());
							System.out.println(ip + " => " + c.referenceDoc + " => " + docs.get(c.topicID + ":" + c.referenceDoc).originalText.substring(ip.startPos, ip.endPos));
						}
						System.out.println("our sentence markers:");
						for (int x=0; x<predictions.get(c).size(); x++) {
							//System.out.println(predictions.get(c).get(x));
						}
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
		
		Set<String> vocab = new HashSet<String>();
		for (Integer t : tmo.topicToWordProbabilities.keySet()) {
			vocab.addAll(tmo.topicToWordProbabilities.get(t).keySet());
		}
		System.out.println("# unique words (aka types):" + vocab.size());
		
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
		for (String w : vocab) {
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
			List<String> citanceWords = removeStopwords(c.getTextTokensAsList());
			double[] citanceDistribution = getSentenceDistribution(citanceWords, wordToTopicProbs, numTopics);

			for (String w : citanceWords) {
				if (!vocab.contains(w)) {
					wordsNotFound.add(w);
					System.out.println("didn't find " + w);
				}
			}
			

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences.  exiting!");
				System.exit(1);
			}
			for (Sentence s : d.sentences) {
				List<String> curReferenceTypes = removeStopwords(s.tokens);
				//System.out.println("sentence types:" + curReferenceTypes);
				
				double[] referenceSentDistribution = getSentenceDistribution(curReferenceTypes, wordToTopicProbs, numTopics);
				
				double cosineScore = getCosineSim(citanceDistribution, referenceSentDistribution);
				double klScore = getKL(citanceDistribution, referenceSentDistribution);
				
				sentenceScores.put(s, cosineScore);
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
			if (sentenceMarkers.size() == 0) {
				System.out.println("we have 0 sentence markers for citance " + c);
			}
		}
		System.out.println("# unique words NOT FOUND (aka types):" + wordsNotFound.size());
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
			Set<String> citanceTypes = removeStopwords(c.getTextTokensAsSet());
			
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);
			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			//System.out.println(c.referenceDoc);
			//System.out.println(docs);
			
			Random rand = new Random();
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
				
				sentenceScores.put(s, rand.nextDouble());
			}
			
			Annotation perfect = c.annotations.get(rand.nextInt(c.annotations.size()));
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
			Set<String> citanceTypes = removeStopwords(c.getTextTokensAsSet());
			//System.out.println("CITANCE: " + citanceTypes);
			//System.out.println("citance " + c.topicID + "_" + c.citanceNum + " has " + c.annotations.size() + " annotations");//citance types:" + citanceTypes);

			// looks within the relevant reference doc (aka source doc)
			Document d = docs.get(c.topicID + ":" + c.referenceDoc);
			if (d.sentences.size() == 0) {
				System.err.println(d + " has 0 sentences");
				System.exit(1);
			}
			for (Sentence s : d.sentences) {
				Set<String> curReferenceTypes = removeStopwords(s.types);
				//System.out.println("REFERENCE:" + curReferenceTypes);
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

	private static Set<String> removeStopwords(Set<String> tokens) {
		Set<String> ret = new HashSet<String>();
		for (String t : tokens) {
			if (!stopwords.contains(t)) {
				ret.add(t);
			}
		}
		return ret;
	}

	private static List<String> removeStopwords(List<String> tokens) {
		List<String> ret = new ArrayList<String>();
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
