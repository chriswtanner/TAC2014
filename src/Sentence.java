import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;


public class Sentence {
	int startPos;
	int endPos;
	String sentence = "";
	Set<String> types = new HashSet<String>();
	List<String> tokens = new ArrayList<String>();
	
	// constructs a Sentence from indexMarkers and a filteredText (lowercased and separated by ' ' b/w each token)
	public Sentence(int startIndex, int endIndex, String s) {
		this.startPos = startIndex;
		this.endPos = endIndex;
		this.sentence = s.trim();
		
		//System.out.println("receiving:" + s);
		StringTokenizer st = new StringTokenizer(s, " ,.;\"");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			types.add(token);
			tokens.add(token);
		}
	}
	
	// returns the maximum precision that the given sentence has with any of the anno's reference/golden answers
	// this is used because our delimitation of Sentences probably doesn't correspond perfectly w/ the golden answers,
	// so we need to know how close/good each of our Sentences is, which will be used for displaying goodness vs Sentence location within a doc 
	public double getPrecision(List<Annotation> annos) {

		int maxBytesShared = 0;
		for (Annotation a : annos) {
			
			int numBytesShared = 0;
			
			for (int i=startPos; i<=endPos; i++) {
				if (a.goldenBytes.contains(i)) {
					numBytesShared++;
				}
			}
			if (numBytesShared > maxBytesShared) {
				maxBytesShared = numBytesShared;
			}
		}
		return (double)maxBytesShared / (double)(endPos - startPos + 1);
	}
	
	public String toString() {
		return "(" + startPos + "," + endPos + "): " + sentence;
	}
}
