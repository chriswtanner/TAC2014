import pdb 

import numpy as np

import sklearn
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.feature_extraction.text import TfidfTransformer
from sklearn.cross_validation import KFold
from sklearn.grid_search import GridSearchCV
from sklearn.svm import SVC
from sklearn.svm import LinearSVC

## there are muliple annotations per topic/citance -- you need to do CV based on these!!
## citance and topic ID fully specify unique cases
class Annotation():

    TOPIC_INDEX = 0
    CITANCE_NUM_INDEX = 1
    CITATION_TEXT_INDEX = 7
    DISCOURSE_FACET_INDEX = -3


    def __init__(self, line, split_char="|"):
        split_line = line.split(split_char)

        def get_value(line_, i, split_token, to_int=False):
            val = line_[i].split(split_token)[1].strip()
            if to_int:
                val = int(val)
            return val

        self.topic_id = get_value(split_line, self.TOPIC_INDEX, "Topic ID:")
        self.citance_num = get_value(split_line, self.CITANCE_NUM_INDEX, "Citance Number:", to_int=True)
        self.citation_text = get_value(split_line, self.CITATION_TEXT_INDEX, "Citation Text:")
        self.discourse_facet = get_value(split_line, self.DISCOURSE_FACET_INDEX, "Discourse Facet:")
        

    def Xy(self):
        return (self.citation_text, self.discourse_facet)

    def u_citance_id(self):
        return self.topic_id + "-" + self.citation_num




def to_Xy():
    citation_texts, discourse_facets = [], []
    for annotation in load_annotations():
        X, y = annotation.Xy()
        citation_texts.append(X)
        discourse_facets.append(y)

    # vectorizer
    vectorizer = CountVectorizer(ngram_range=(1,2), stop_words="english", binary=False)
    X = vectorizer.fit_transform(citation_texts)
    transformer = TfidfTransformer(smooth_idf=True)
    X = transformer.fit_transform(X)
    return X, np.array(discourse_facets)

def cv():
    X, y = to_Xy()
    kf = KFold(X.shape[0], n_folds=5, shuffle=True)
    accuracies, precs, recalls, fs = [], [], [], []
    for train, test in kf:
        svc = LinearSVC()
        parameters = {'C':[.00001, .0001, .001, .05, .01, .1]}
        clf = GridSearchCV(svc, parameters, scoring='f1')
        clf.fit(X[train], y[train])
        preds = clf.predict(X[test])
        y_true = y[test]
        #pdb.set_trace()
        #prec, recall, f, support = \
        #    sklearn.metrics.precision_recall_fscore_support(y_true, preds, beta=1)
        
        #precs.append(prec)
        #recalls.append(recall)
        acc = sklearn.metrics.accuracy_score(y_true, preds)
        accuracies.append(acc)
        #f = sklearn.metrics.f1_score(y_true, preds)
        #print sklearn.metrics.classification_report(y_true, preds)
        #fs.append(f)

        #accuracy
    
    #print "mean f: %s" % np.mean(np.array(fs))
    #return 
    print "mean accuracy: %s" % np.mean(np.array(accuracies))



def load_annotations(fname="annoLegend.txt"):
    annotations = []
    with open(fname) as annotations_data:
        lines = annotations_data.readlines()
        annotations = [Annotation(line) for line in lines]

    return annotations