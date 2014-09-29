import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class Citance {
	String topicID = "";
	int citanceNum = 0;
	String referenceDoc = "";
	String citingDoc = "";
	String citationText = ""; // we try to remove all text within ( ) because it usually contains author info
	String discourse = "";
	List<Annotation> annotations = new ArrayList<Annotation>();
	
	public Citance(String topic, int citNum, String ref, String citingDoc, String text) {
		this.topicID = topic;
		this.citanceNum = citNum;
		this.referenceDoc = ref;
		this.citingDoc = citingDoc;
		int parenCount = 0;
		int bracketCount = 0;
		for (int i=0; i<text.length(); i++) {
			if (text.charAt(i) == '(') {
				parenCount++;
			} else if (text.charAt(i) == ')') {
				parenCount--;
			} else if (text.charAt(i) == '[') {
				bracketCount++;
			} else if (text.charAt(i) == ']') {
				bracketCount--;
			} else if (parenCount == 0 && bracketCount == 0) {
				this.citationText += text.charAt(i);
			}
		}
		this.citationText = this.citationText.trim();
	}
	
	// returns lowercased tokens as a list
	public List<String> getTextTokensAsList() {
		List<String> ret = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(citationText.toLowerCase(), " ,.;\"");
		while (st.hasMoreTokens()) {
			ret.add(st.nextToken());
		}
		return ret;
	}
	
	// returns lowercased tokens as a set
	public Set<String> getTextTokensAsSet() {
		Set<String> ret = new HashSet<String>();
		StringTokenizer st = new StringTokenizer(citationText.toLowerCase(), " ,.;\"");
		while (st.hasMoreTokens()) {
			ret.add(st.nextToken());
		}
		return ret;
	}
	
	
	public void addAnnotation(Annotation a) {
		annotations.add(a);
	}

	public void addDiscourse(String d) {
		this.discourse = d;
		
	}
}
