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