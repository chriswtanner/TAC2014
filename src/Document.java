import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


public class Document {
	String topicID = "";
	String name = "";
	String originalText = "";
	
	// represents the start and end character positions of all sentence, relative to 'originalText'
	// e.g., originalText will start with header info, 
	// but if we have (142,181) that means chars 142 - 181 within originalText represents a valid sentence we care about
	List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
	
	// constructs a new Doc from the passed-in text file
	public Document(String docDir, String topicID, String sourceName) throws IOException {

		this.topicID = topicID;
		this.name = sourceName;
		String file = docDir + "data/" + topicID + "/Documents_Text/" + sourceName;

		System.out.println("creating doc: " + file);
		BufferedReader bin = new BufferedReader(new FileReader(file));
		String curLine = "";
		
		// reads each line of annoInputFile, while looking for 'topicID' and 'reference article' fields,
		// in order to know the proper location of the file for which we wish to create a Document
		while ((curLine = bin.readLine())!=null) {
			//StringTokenizer st = new StringTokenizer(curLine);
			originalText += curLine;
		}
		
		if (name.equals("Voorhoeve.txt")) {
			System.out.println(originalText.substring(1179, 1547));
		}
	}
	
	// represents the start and end character positions of a sentence, relative to 'originalText'
	class IndexPair {
		int startPos;
		int endPos;
		IndexPair(int s, int e) {
			this.startPos = s;
			this.endPos = e;
		}
	}
}
