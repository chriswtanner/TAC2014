
public class Sentence {
	int startPos;
	int endPos;
	String sentence = "";
	public Sentence(int startIndex, int endIndex, String s) {
		this.startPos = startIndex;
		this.endPos = endIndex;
		this.sentence = s.trim();
	}
	
	public String toString() {
		return "(" + startPos + "," + endPos + "): " + sentence;
	}
}
