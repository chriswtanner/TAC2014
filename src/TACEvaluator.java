import java.io.IOException;


public class TACEvaluator {

	public static void main(String[] args) throws IOException {
		
		// NOTE: simply change this value
		String dataDir = "/Users/christanner/research/projects/TAC2014/eval/";
		boolean runLDA = false;
		
		// LDA's input files
		String annoInputFile = dataDir + "annoLegend.txt";
		String malletInputFile = dataDir + "mallet-tac.txt";
		String stopwords = dataDir + "stopwords.txt";

		// LDA's output/saved object which will be written to if 'runLDA = true'; otherwise, it can be read from
		String ldaObject = dataDir + "lda_2000i.ser";
		
		// NOTE: LDA variables/params are in the LDA's class as global vars
		if (runLDA) {
			LDA l = new LDA(malletInputFile, stopwords);
			l.runLDA();
			l.saveLDA(ldaObject);
		}
	}

}
