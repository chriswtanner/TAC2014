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
	List<IndexPair> sentenceMarkers = new ArrayList<IndexPair>();
	
	List<String> introSections = new ArrayList<String>(Arrays.asList("Introduction", "Summary", "Main Text", "Abstract"));
	List<String> endPunctuations = new ArrayList<String>(Arrays.asList(".", ";", "!", "?"));
	
 	// constructs a new Doc from the passed-in text file
	public Document(String docDir, String topicID, String sourceName) throws IOException {

		this.topicID = topicID;
		this.name = sourceName;
		String file = docDir + "data/" + topicID + "/Documents_Text/" + sourceName;

		System.out.println("creating doc: " + file);
		//byte[] encoded = Files.readAllBytes(Paths.get(file));
	    File f = new File(file);
	    FileInputStream fin = new FileInputStream(f);
	    byte[] buffer = new byte[(int) f.length()];
	    new DataInputStream(fin).readFully(buffer);
	    fin.close();
	    String originalText = new String(buffer, "UTF-8");
		/*
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
		*/
	    
	    // iterates through the 'originalText' string, trying to determine where sentence markers should be;
	    // we'll ignore the headers, each newline represents the end of a new sentence, start of a new one,
	    // and if we see endPunctuation (.!?;) followed by a space, we'll require that we've seen at least 40 chars to make a sentence
	    if (name.equals("Voorhoeve.txt")) {
	    	
	    	boolean foundIntro = false;
	    	int startIndex = 0;
		    for (int i=0; i<originalText.length(); i++) {
		    	char c = originalText.charAt(i);
		    	
		    	// checks if we just started a new line
		    	if (i>0 && originalText.charAt(i-1) == '\n') {
		    		startIndex = i;
		    		System.out.println("setting starting index: " + startIndex);
		    	}
		    	//String curLine = "";
		    	
		    	// found a newline; let's potentially make a new start and end marker
		    	boolean isEndPunct = false;
		    	for (String ep :  endPunctuations) {
		    		if (Character.toString(c).equals(ep)) {
		    			isEndPunct = true;
		    			System.out.println("we found endPunct: " + ep);
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
		    	
		    	
		    	// we found an end marker!
		    	if (foundIntro && (i - startIndex) >= minNumCharsPerSentence && (c == '\n' || isEndPunct)) {
		    		sentenceMarkers.add(new IndexPair(startIndex, i));
		    	} else if (c == '\n' && !foundIntro) {
	    			boolean isIntroSection = false;
	    			
	    			String curLine = originalText.substring(startIndex, i);
	    			System.out.println("curline:" + curLine);
	    			for (String intro : introSections) {
	    				
	    				if (curLine.equals(intro)) {
		    				isIntroSection = true;
		    			}
	    			}
	    			if (isIntroSection) {
	    				foundIntro = true;
	    				System.out.println("we found an intro!!; skipping to the end of this title");
	    				i = originalText.indexOf("\n", i);
	    				System.out.println("we are skipping to index: " + i + " = " + originalText.charAt(i));
	    			}
		    	}
		    }
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
