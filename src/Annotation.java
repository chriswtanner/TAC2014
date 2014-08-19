import java.util.ArrayList;
import java.util.List;

// represents an Annotator's annotation for 1 particular Citance
public class Annotation {
	List<IndexPair> referenceOffsets = new ArrayList<IndexPair>();
	String referenceText = "";
	String discourse = "";
	String annotator = "";
	
	public Annotation(List<String> offsets, String text, String dis, String anno) {
		referenceOffsets = new ArrayList<IndexPair>();
		for (String o : offsets) {
			String[] tokens = o.split("-");
			referenceOffsets.add(new IndexPair(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])));
		}
		this.referenceText = text;
		this.discourse = dis;
		this.annotator = anno;
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
