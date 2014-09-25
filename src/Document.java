import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/*
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
*/
public class Document {
	String topicID = "";
	String name = "";
	Set<Integer> bytesSpanned = new HashSet<Integer>();
	String originalText = "";
	int minNumCharsPerSentence = 40;
	
	// represents the start and end character positions of all sentence, relative to 'originalText'
	// e.g., originalText will start with header info, 
	// but if we have (142,181) that means chars 142 - 181 within originalText represents a valid sentence we care about
	List<Sentence> sentences = new ArrayList<Sentence>();
	List<Integer> sectionMarkers = new ArrayList<Integer>(); // represents how many sentences we've seen so far, which precede the current section we just saw
	List<String> sectionNames = new ArrayList<String>();
	List<Integer> paragraphMarkers = new ArrayList<Integer>();
	
	
	List<String> introSections = new ArrayList<String>(Arrays.asList("open archive", "introduction", "summary", "main text", "abstract"));
	List<String> endPunctuations = new ArrayList<String>(Arrays.asList(".", ";", "!", "?", "\n"));
	List<String> endSections = new ArrayList<String>(Arrays.asList("acknowledgments", "references", "footnotes"));
	List<String> badFilteredChars = new ArrayList<String>(Arrays.asList(".", ",", "-"));
	
  	// constructs a new Doc from the passed-in text file
	public Document(String docDir, String topicID, String sourceName) throws IOException {

		this.topicID = topicID;
		this.name = sourceName;
		String file = docDir + "data/" + topicID + "/Documents_Text/" + sourceName;

		System.out.println("creating doc: " + file);
		if (!sourceName.equals("Sherr.txt")) {
			//return;
		}
	    File f = new File(file);
	    FileInputStream fin = new FileInputStream(f);
	    originalText = IOUtils.toString(fin, "UTF-8").replaceAll("\r", ""); //FileUtils.readFileToString(f);
	    bytesSpanned = new HashSet<Integer>(); //new byte[originalText.length()];
	    
	    fin.close();
	    
	    // iterates through the 'originalText' string, trying to determine where sentence markers should be;
	    // we'll ignore the headers, each newline represents the end of a new sentence, start of a new one,
	    // and if we see endPunctuation (.!?;) followed by a space, we'll require that we've seen at least 40 chars to make a sentence
    	boolean foundIntro = false;
    	int bracketCount = 0;

    	// new, simplified approach
    	int lastSentenceMarker = 0;
    	boolean lastMarkerWasNewLine = true;
    	
	    for (int i=0; i<originalText.length(); i++) {

	    	char c = originalText.charAt(i);
	    	if (c == '(') {
	    		bracketCount++;
	    	} else if (c == ')') {
	    		bracketCount = Math.max(0, bracketCount-1);
	    	} else {
	    	
		    	// checks if current char is an endPunct char (e.g. .,;!?)
		    	boolean isEndPunct = false;
		    	for (String ep : endPunctuations) {
		    		if (Character.toString(c).equals(ep)) {
		    			isEndPunct = true;
		    		}
		    	}
		    	
		    	// newlines clear the bracketCount
		    	if (c == '\n') {
		    		bracketCount = 0;
		    		
		    		// checks if we're entering a new paragraph!
		    		if (i>0 && originalText.charAt(i-1) == '\n') {
		    			if (!paragraphMarkers.contains(sentences.size())) { 
		    				paragraphMarkers.add(sentences.size());
		    			}
		    		}
		    	}
		    	
		    	// although we found an end-punctuation, we still need to ensure that it's the end of the sentence
		    	// by checking that the next char is EOF, \n or a space
		    	if (isEndPunct && c != '\n') {
		    		if (i < originalText.length()-1) {
		    			char nextChar = originalText.charAt(i+1);
		    			if (!(nextChar == ' ' || nextChar == '\n')) {
		    				isEndPunct = false;
		    			}
		    		}
		    	}
		    	
		    	// * checks if we actually care to do anything with the current line
		    	// we def have endPunctuation, but let's make sure we reached the end of a sentence/line
		    	// then check if we have:
		    	// (1) a section name (intro or end)
		    	// (2) a sentence we wish to save
		    	if (isEndPunct && bracketCount == 0) {

			    	// we found an end marker!  so, we potentially have a sentence; let's trim the beginning then
			    	for (int j=lastSentenceMarker; j<i; j++) {
			    		if (originalText.charAt(j) != ' ' || originalText.charAt(j) != '\t') {
			    			lastSentenceMarker = j;
			    			break;
			    		}
			    	}
			    	
		    		String curString = originalText.substring(lastSentenceMarker, i+1).trim(); // this includes character i
		    		String lowercased = curString.toLowerCase();
		    		// (1) checks if we have a section name
		    		boolean foundSection = false;
		    		if (c == '\n') {
		    			
		    			boolean isEndSection = false;
		    			// checks if it's an 'end section'
		    			for (String es : endSections) {
			    			if (lowercased.equals(es)) {
				    			System.out.println("found end section?:" + es);
			    				isEndSection = true;
			    			}
			    		}
			    		if (isEndSection) {
			    			break;
			    		}
			    		
		    			boolean isIntroSection = false;
		    			// checks if it's an 'intro section'
		    			for (String intro : introSections) {
		    				if (lowercased.equals(intro)) {
			    				isIntroSection = true;
			    				System.out.println("we found intro?:" + intro);
			    			}
		    			}
		    			
		    			if (isIntroSection && lastMarkerWasNewLine) {
		    				foundSection = true;
		    				
		    				// checks if this is the 1st time we've seen an 'intro section'
		    				if (!foundIntro) {
		    				
		    					foundIntro = true;
		    				
		    					// clear all Sentence info that we've accumulated thus far
		    					sentences.clear();
		    					sectionMarkers.clear();
		    					sectionNames.clear();
		    					paragraphMarkers.clear();
		    					paragraphMarkers.add(0);
		    				}
		    				// it's a new section, so let's add the marker
		    				sectionMarkers.add(sentences.size());
		    				sectionNames.add(curString);	
		    			// not an 'intro section,' but could still be a section we care about
		    			} else if (lastMarkerWasNewLine) {
				    		boolean containsAlpha = false;
				    		for (int eachChar=0; eachChar<curString.length(); eachChar++) {
				    			if ((curString.charAt(eachChar) >= 65 && curString.charAt(eachChar) <= 90) ||
				    					(curString.charAt(eachChar) >= 97 && curString.charAt(eachChar) <= 122)) {
				    				containsAlpha = true;
				    				break;
				    			}
				    		}
				    		// we found a new section
				    		if (containsAlpha && curString.length() > 5 && curString.length() < 60 && !curString.startsWith("Fig.") && !curString.contains("<")
				    			&& !curString.startsWith("Full-size image") && !curString.startsWith("Figure ") && !curString.startsWith("igure ")  && !curString.startsWith("Download")
				    			&& !curString.startsWith("View ") && !curString.startsWith("Previous Section") && !curString.startsWith("Next Section")
				    			&& !curString.startsWith("In this ") && !curString.startsWith("Keywords:") && !curString.startsWith("Formula") && !curString.startsWith("ormula")
				    			&& !curString.startsWith("PDF ") && !curString.startsWith("Go to") && !curString.startsWith("See also ")
				    			&& !curString.startsWith("Show more") && !curString.startsWith("http://")
				    			&& !curString.startsWith("Table") && !curString.startsWith("able ") && !curString.startsWith("Object name is") && !curString.contains("%")
				    			&& !curString.trim().equals("") && !curString.contains("\t")) {
				    			
				    			System.out.println("found section:" + curString);
				    			sectionMarkers.add(sentences.size());
				    			sectionNames.add(curString);
				    			foundSection = true;
				    		}
		    			}
			    	} // end of checking if c == '\n'
		    		
		    		// only if we didn't just find a Section can we consider the current line (curString)
		    		// to be a Sentence
		    		if (!foundSection && foundIntro) {
		    			
		    			if ((i - lastSentenceMarker) >= minNumCharsPerSentence) {
		    	    		
		    	    		String filteredText = filterText(originalText.substring(lastSentenceMarker,i+1));
		    	    		
		    	    		if (filteredText.indexOf("et al.") != filteredText.length()-6) {
			    	    		Sentence s = new Sentence(lastSentenceMarker+1, i, filteredText);
			    	    		sentences.add(s);
			    	    		lastSentenceMarker = i+1;
			    	    		
			    	    		if (c == '\n') {
					    			lastMarkerWasNewLine = true;
					    		} else {
					    			//System.out.println("lastmarkerwasntnewline:" + curString + "END");
					    			lastMarkerWasNewLine = false;
					    		}
		    	    		}
		    			}
		    		}

		    		// we found a new line, so let's adjust our lastSentenceMarker if we:
		    		// - found a new section\n OR
		    		// - haven't found intro yet;
		    		// thus, if we have foundAnIntro already, the current line better be a legit Section,
		    		// otherwise, we don't update our lastSentenceMarker
		    		if (c == '\n') {// && (foundSection || !foundIntro)) {
		    			lastMarkerWasNewLine = true;
		    			lastSentenceMarker = i+1;
		    		}
		    		
		    	} // end of ensuring we have endPunct and bracketCount == 0
	    	}
	    }
    	
    	
    	/*
	    for (int i=0; i<originalText.length(); i++) {

	    	char c = originalText.charAt(i);	    	
	    	if (c == '(') {
	    		bracketCount++;
	    	} else if (c == ')') {
	    		bracketCount = Math.max(0, bracketCount-1);
	    	}
	    	
	    	// checks if we just started a new line
	    	if (i>0 && originalText.charAt(i-1) == '\n') {
	    		
	    		
	    		String curWord = originalText.substring(startIndex,i).trim();
	    		// finds the prev \n (this ensures we don't have 'sections' which are simply the suffix of a line which has the preceeding . counting as the startIndex)
	    		int lastNewline = i-2;
	    		while (lastNewline>startIndex) {
	    			if (originalText.charAt(lastNewline) == '\n') {
	    				System.out.println("curWord was:" + curWord);
	    				curWord = originalText.substring(lastNewline+1,i).trim();
	    				System.out.println("*** but now:" + curWord);
	    				break;
	    			}	    			
	    			lastNewline--;
	    		}

	    		boolean containsAlpha = false;
	    		for (int eachChar=0; eachChar<curWord.length(); eachChar++) {
	    			if ((curWord.charAt(eachChar) >= 65 && curWord.charAt(eachChar) <= 90) ||
	    					(curWord.charAt(eachChar) >= 97 && curWord.charAt(eachChar) <= 122)) {
	    				containsAlpha = true;
	    				break;
	    			}
	    		}
	    		String[] tokens = curWord.split(" ");
	    		if (sourceName.equals("Huang.txt")) {
	    			System.out.println("candidate:" + curWord);
	    		}
	    		// we found a new section
	    		if (containsAlpha && curWord.length() > 5 && curWord.length() < 80 && !curWord.startsWith("Fig.") && !curWord.contains("<")
	    			&& !curWord.startsWith("Full-size image") && !curWord.startsWith("Figure ") && !curWord.startsWith("igure ")  && !curWord.startsWith("Download")
	    			&& !curWord.startsWith("View ") && !curWord.startsWith("Previous Section") && !curWord.startsWith("Next Section")
	    			&& !curWord.startsWith("In this ") && !curWord.startsWith("Formula") && !curWord.startsWith("ormula")
	    			&& !curWord.startsWith("PDF ") && !curWord.startsWith("Go to")
	    			&& !curWord.startsWith("Table") && !curWord.startsWith("able ") && !curWord.startsWith("Object name is") && !curWord.contains("%")
	    			&& !curWord.trim().equals("") && !curWord.contains("\t")) {
	    			
	    			
	    			//System.out.println("valid section title:" + curWord);
	    			sectionMarkers.add(sentences.size());
	    			sectionNames.add(curWord);
	    		}
	    		
	    		//System.out.println("curWord:" + curWord);
	    		boolean reachedEnd = false;
	    		for (String es : endSections) {
	    			if (curWord.equals(es)) {
	    				//System.out.println("found:" + es);
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
	    	if ((i - startIndex) >= minNumCharsPerSentence && (c == '\n' || isEndPunct) && bracketCount == 0) {
	    		
	    		i++;
	    		String filteredText = filterText(originalText.substring(startIndex, i));
	    		Sentence s = new Sentence(startIndex, i, filteredText); // was i-1
	    		sentences.add(s);
	    		startIndex = i+1;
	    		bracketCount = 0;
	    	    
	    	} else if (c == '\n' && !foundIntro) {
    			boolean isIntroSection = false;
    			
    			String curLine = originalText.substring(startIndex, i).trim();
    			
    			for (String intro : introSections) {
    				
    				if (curLine.equals(intro)) {
	    				isIntroSection = true;
	    				//System.out.println("we found:" + intro);
	    			}
    			}
    			if (isIntroSection) {
    				foundIntro = true;
    				sentences.clear();
    				sectionMarkers.clear();
    				sectionNames.clear();
    				int beg = i;
    				while (i < originalText.length()) {
    					if ((char)originalText.charAt(i) == '\n') {
    						break;
    					}
    					i++;
    				}
					//System.out.println("i was " + beg + " but now" + i);
    				//i = originalText.indexOf("\n", i);
    			}
	    	}
	    }
	    */
	    
	    for (String s : sectionNames) {
	    	System.out.println("section name: " + s);
	    }
	    
	    for (int i=0; i<sentences.size(); i++) {
	    	if (paragraphMarkers.contains(i)) {
	    		System.out.println("***");
	    	}
	    	Sentence s = sentences.get(i);
	    	System.out.println("sent:" + s);
	    	System.out.println("extr:(" + s.startPos + "," + s.endPos + "):" + originalText.substring(s.startPos, s.endPos+1));
	    }
	    
	    /*
	    System.out.println("# sentences: " + sentences.size());
	    if (sourceName.equals("Kumar.txt")) {
	    	System.out.println("original text: "  + originalText);
	    	System.out.println("last sentence: " + sentences.get(sentences.size()-1));
	    	//System.exit(1);
	    }
	    */
	    //System.exit(1);
	}

	public String filterText(String text) {
		String textPhase1 = "";
		int parenCount = 0;
		int bracketCount = 0;
		for (int i=0; i<text.length(); i++) {
			if (text.charAt(i) == '(') {
				parenCount++;
			} else if (text.charAt(i) == ')') {
				parenCount = Math.max(0, parenCount-1);
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
			String token = st.nextToken();
			if (!badFilteredChars.contains(token)) {
				ret += (token + " ");
			}
		}
		return ret.trim();
	}

	public void fillBytes(IndexPair sentenceMarkers) {
		for (int i=sentenceMarkers.startPos; i<=sentenceMarkers.endPos; i++) {
			bytesSpanned.add(i); //bytesSpanned[i] = 1;
		}
	}
	
	public void clearBytes() {
		bytesSpanned = new HashSet<Integer>(); //new byte[originalText.length()];
	}
	public String toString() {
		return this.name + " (" + this.topicID + ") has " + this.sentences.size() + " sentences";
	}

	public double calculateWeightedF1(List<Annotation> annotations) {
		
		// calculates weighted recall
		int numBytesShared = 0;
		int recallDenom = 0;
		for (Annotation a : annotations) {
			for (Integer byteFilled : a.goldenBytes) {
				if (this.bytesSpanned.contains(byteFilled)) {
					numBytesShared++;
				}
			}
			recallDenom += a.totalPos;
		}
		double wRecall = 0.000001 + (double)numBytesShared / (double)recallDenom;

		// calculates weighted precision
		double precisionDenom = (double)this.bytesSpanned.size()*annotations.size();
		double wPrecision = 0.000001 + (double)numBytesShared / precisionDenom;
		
		return 2*(wPrecision*wRecall)/(wPrecision + wRecall);
	}
}
