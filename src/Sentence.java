import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;


public class Sentence {
	int startPos;
	int endPos;
	String sentence = "";
	Set<String> types = new HashSet<String>();
	
	public Sentence(int startIndex, int endIndex, String s) {
		this.startPos = startIndex;
		this.endPos = endIndex;
		this.sentence = s.trim();
		
		//System.out.println("receiving:" + s);
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens()) {
			types.add(st.nextToken());
		}
		
	}
	
	public String toString() {
		return "(" + startPos + "," + endPos + "): " + sentence;
	}
}
