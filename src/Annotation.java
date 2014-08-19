import java.util.ArrayList;
import java.util.List;

// represents an Annotator's annotation for 1 particular Citance
public class Annotation {
	List<IndexPair> referenceOffsets = new ArrayList<IndexPair>();
	List<Boolean[]> answerGrids = new ArrayList<Boolean[]>();
	int filledPos = 0;
	int totalPos = 0;
	String referenceText = "";
	String discourse = "";
	String annotator = "";
	
	public Annotation(List<String> offsets, String text, String dis, String anno) {
		referenceOffsets = new ArrayList<IndexPair>();
		for (String o : offsets) {
			String[] tokens = o.split("-");
			referenceOffsets.add(new IndexPair(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])));
			
			// i.e., 100 - 101 indices should span 2 cells; 2 = 1 + 101 - 100
			answerGrids.add(new Boolean[1 + Integer.parseInt(tokens[1]) - Integer.parseInt(tokens[0])]);
			totalPos += (1 + Integer.parseInt(tokens[1]) - Integer.parseInt(tokens[0]));
		}
		//System.out.println("totalPos:" + totalPos + "; offsets:" + referenceOffsets);
		
		this.referenceText = text;
		this.discourse = dis;
		this.annotator = anno;
	}
	
	// accepts a given sentence's start and end markers and records into answerGrids which characters we span
	public double fillInSentence(int start, int end) {
		
		for (int refOffPairNum=0; refOffPairNum<referenceOffsets.size(); refOffPairNum++) {
			IndexPair ip = referenceOffsets.get(refOffPairNum);

			for (int i=start; i<=end; i++) {
				if (i>=ip.startPos && i<=ip.endPos) {
					

					// represents the index pos in the boolean[]; e.g., 100 - 101 boolean span (start and end pos) would have i=100 fill into cell 0 
					int modifiedIndex = i - ip.startPos; 

					//System.out.println("i:" + i + "; start:" + ip.startPos  + "; end:" + ip.endPos + "; modifiedIndex:" + modifiedIndex);
					//System.out.println(answerGrids.get(refOffPairNum).length);
					if (answerGrids.get(refOffPairNum)[modifiedIndex] == null) {
						filledPos++;
						answerGrids.get(refOffPairNum)[modifiedIndex] = true;
					}
				}
			}
		}
		
		return (double)filledPos / (double)totalPos;
	}
	
	public String toString() {
		return "anno:" + referenceOffsets + ";" + referenceText + ";" + discourse + ";" + annotator;
	}
	
 	// represents the start and end character positions of a sentence, relative to the containing doc (the reference doc)
 	class IndexPair {
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
