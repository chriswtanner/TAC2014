import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;


public class Document {
	String topicID = "";
	String name = "";
	String originalText = "";
	
	int minNumCharsPerSentence = 40;
	
	// represents the start and end character positions of all sentence, relative to 'originalText'
	// e.g., originalText will start with header info, 
	// but if we have (142,181) that means chars 142 - 181 within originalText represents a valid sentence we care about
	List<Sentence> sentences = new ArrayList<Sentence>();
	
	List<String> introSections = new ArrayList<String>(Arrays.asList("Introduction", "Summary", "Main Text", "Abstract"));
	List<String> endPunctuations = new ArrayList<String>(Arrays.asList(".", ";", "!", "?"));
	List<String> endSections = new ArrayList<String>(Arrays.asList("Acknowledgments", "References", "Footnotes"));
	
 	// constructs a new Doc from the passed-in text file
	public Document(String docDir, String topicID, String sourceName) throws IOException {

		this.topicID = topicID;
		this.name = sourceName;
		String file = docDir + "data/" + topicID + "/Documents_Text/" + sourceName;

		System.out.println("creating doc: " + file);
	    File f = new File(file);
	    FileInputStream fin = new FileInputStream(f);
	    byte[] buffer = new byte[(int) f.length()];
	    new DataInputStream(fin).readFully(buffer);
	    fin.close();
	    this.originalText = new String(buffer, "UTF-8");
	    
	    // iterates through the 'originalText' string, trying to determine where sentence markers should be;
	    // we'll ignore the headers, each newline represents the end of a new sentence, start of a new one,
	    // and if we see endPunctuation (.!?;) followed by a space, we'll require that we've seen at least 40 chars to make a sentence
    	boolean foundIntro = false;
    	int startIndex = 0;
    	int bracketCount = 0;
	    for (int i=0; i<originalText.length(); i++) {
	    	char c = originalText.charAt(i);
	    	
	    	if (c == '(') {
	    		bracketCount++;
	    	} else if (c == ')') {
	    		bracketCount = Math.max(0, bracketCount-1);
	    	}
	    	
	    	// checks if we just started a new line
	    	if (i>0 && originalText.charAt(i-1) == '\n') {
	    		
	    		String curWord = originalText.substring(startIndex, i).trim();
	    		boolean reachedEnd = false;
	    		for (String es : endSections) {
	    			if (curWord.equals(es)) {
	    				System.out.println("found:" + es);
	    				reachedEnd = true;
	    			}
	    		}
	    		if (reachedEnd) {
	    			break;
	    		}
	    		startIndex = i;
	    		bracketCount = 0;
	    	}

	    	// checks if current char is an endPunct char (e.g. .,;!?)
	    	boolean isEndPunct = false;
	    	for (String ep :  endPunctuations) {
	    		if (Character.toString(c).equals(ep)) {
	    			isEndPunct = true;
	    		}
	    	}
	    	
	    	// although we found an end-punctuation, we still need to ensure that it's the end of the sentence
	    	// by checking that the next char is EOF, \n or a space
	    	if (isEndPunct) {
	    		if (i < originalText.length()-1) {
	    			char nextChar = originalText.charAt(i+1);
	    			if (!(nextChar == ' ' || nextChar == '\n')) {
	    				isEndPunct = false;
	    			}
	    		}
	    	}
	    	
	    	// we found an end marker!  so, we potentially have a sentence; let's trim the beginning then
	    	for (int j=startIndex; j<i; j++) {
	    		if (originalText.charAt(j) != ' ') {
	    			startIndex = j;
	    			break;
	    		}
	    	}
	    	if (/*foundIntro && */(i - startIndex) >= minNumCharsPerSentence && (c == '\n' || isEndPunct) && bracketCount == 0) {
	    		
	    		if (c != '\n') {
	    			i++;
	    		}
	    		
	    		String filteredText = filterText(originalText.substring(startIndex, i));
	    		Sentence s = new Sentence(startIndex, i-1, filteredText);
	    		sentences.add(s);
	    		startIndex = i+1;
	    		bracketCount = 0;
	    	} else if (c == '\n' && !foundIntro) {
    			boolean isIntroSection = false;
    			
    			String curLine = originalText.substring(startIndex, i);
    			for (String intro : introSections) {
    				
    				if (curLine.equals(intro)) {
	    				isIntroSection = true;
	    				//System.out.println("we found:" + intro);
	    			}
    			}
    			if (isIntroSection) {
    				foundIntro = true;
    				sentences.clear();
    				i = originalText.indexOf("\n", i);
    			}
	    	}
	    }
	    System.out.println("# sentences: " + sentences.size());
	}
	
	public String filterText(String text) {
		String textPhase1 = "";
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
				textPhase1 += text.charAt(i);
			}
		}
		StringTokenizer st = new StringTokenizer(textPhase1.toLowerCase());
		String ret = "";
		while (st.hasMoreTokens()) {
			ret += (st.nextToken() + " ");
		}
		return ret.trim();
	}

	public String toString() {
		return this.name + " (" + this.topicID + ") has " + this.sentences.size() + " sentences";
	}
}
