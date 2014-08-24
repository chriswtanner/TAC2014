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
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			types.add(token);
			tokens.add(token);
		}
		
	}
	
	public String toString() {
		return "(" + startPos + "," + endPos + "): " + sentence;
	}
}
