import pdb 
import itertools
import os
import csv 

import numpy as np
import sklearn
from collections import defaultdict
from sklearn.naive_bayes import BernoulliNB, MultinomialNB
#from sklearn.ensemble import BaggingClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.neighbors import KNeighborsClassifier 

from sklearn.feature_extraction.text import CountVectorizer
from sklearn.feature_extraction.text import TfidfTransformer
from sklearn.cross_validation import KFold
from sklearn.grid_search import GridSearchCV
from sklearn.svm import SVC
from sklearn.svm import LinearSVC
from sklearn.linear_model import LogisticRegression as LR


def get_value(line_, i, split_token, to_int=False):
    val = line_[i].split(split_token)[1].strip()
    if to_int:
        val = int(val)
    return val

## there are muliple annotations per topic/citance -- you need to do CV based on these!!
## citance and topic ID fully specify unique cases
class Annotation():

    TOPIC_INDEX = 0
    CITANCE_NUM_INDEX = 1
    CITATION_TEXT_INDEX = 7
    REFERENCE_TEXT_INDEX = 9
    DISCOURSE_FACET_INDEX = -3

    def __init__(self, line, train=True, split_char="|"):
        split_line = line.split(split_char)
        if not train:
            self.CITATION_TEXT_INDEX

        self.topic_id = get_value(split_line, self.TOPIC_INDEX, "Topic ID:")
        self.citance_num = get_value(split_line, self.CITANCE_NUM_INDEX, "Citance Number:")
        self.citation_text = get_value(split_line, self.CITATION_TEXT_INDEX, "Citation Text:")
        self.discourse_facet = None
        if train:
            self.reference_text = get_value(split_line, self.REFERENCE_TEXT_INDEX, "Reference Text:")
            self.discourse_facet = get_value(split_line, self.DISCOURSE_FACET_INDEX, "Discourse Facet:")
        self.line = line # hold on to the line. 
        #pdb.set_trace()
        self.training_example = train

    def Xy(self, stop_word=False, include_reference_text=False, predicted_reference_texts_d=None):
        if include_reference_text:
            stop_words = []
            if stop_word:
                stop_words = sklearn.feature_extraction.stop_words.ENGLISH_STOP_WORDS

            tokens = self.citation_text
            ref_text = None

            if predicted_reference_texts_d is None:
                ref_text = self.reference_text
            else:
                #pdb.set_trace()
                ref_text = predicted_reference_texts_d[self.u_citance_id()]
            
            ref_tokens = " ".join(["REF_%s" % t for t in ref_text.split(" ") if t not in stop_words])
            tokens = u" ".join((unicode(tokens, errors="ignore"), unicode(ref_tokens, errors="ignore")))
            return (tokens, self.discourse_facet)
        else:
            return (self.citation_text, self.discourse_facet)

    def u_citance_id(self):
        return self.topic_id + "-" + self.citance_num
        
# @TODO augment with reference text features!
def to_Xy(annotations, return_vectorizer=False, vectorizer=None, stop_words=None, 
            include_reference_texts=False, include_predicted_reference_texts=False):
    citation_texts, discourse_facets = [], []

    predicted_reference_texts_d = None
    if include_reference_texts and include_predicted_reference_texts:
        print "using *predicted* references!"
        predicted_reference_texts_d = load_predicted_ref_texts_dict()
    
    for annotation in annotations:
        X, y = annotation.Xy(include_reference_text=include_reference_texts, 
                                predicted_reference_texts_d=predicted_reference_texts_d)
        citation_texts.append(X)
        discourse_facets.append(y)

    X = None
    # vectorizer
    if vectorizer is None:
        vectorizer = CountVectorizer(ngram_range=(1,2), stop_words=stop_words, binary=False, min_df=5)
        X = vectorizer.fit_transform(citation_texts)
    else:
        X = vectorizer.transform(citation_texts)

    transformer = TfidfTransformer(smooth_idf=True)
    X = transformer.fit_transform(X)
    if return_vectorizer:
        return X, np.array(discourse_facets), vectorizer
    return X, np.array(discourse_facets)


def make_test_predictions(output_file="predicted_annotations.txt", include_reference_texts=True):
    ### train on all data
    train_annotations = load_annotations()
    X, y, v = to_Xy(train_annotations, return_vectorizer=True, include_reference_texts=include_reference_texts)
    mult_clf, svm_clf, rf_clf, neigh, log_reg_clf = train_models(X,y)

    ### load test data
    test_annotations, test_lines = load_test_annotations()
    test_uids = [a.u_citance_id() for a in test_annotations]
    # TODO add predicted reference texts
    test_X, test_y = to_Xy(test_annotations, include_reference_texts=include_reference_texts, 
                            include_predicted_reference_texts=True, vectorizer=v)
    discourse_predictions = []

    ### prediction time.
    multinomial_preds = mult_clf.predict(test_X)
    svm_preds = svm_clf.predict(test_X)
    lr_preds = log_reg_clf.predict(test_X)
    rf_preds = rf_clf.predict(test_X.toarray())
    nn_preds = neigh.predict(test_X)

    all_preds = [[svm_preds[i], lr_preds[i], multinomial_preds[i], rf_preds[i], nn_preds[i]] 
                    for i in xrange(len(test_y))]
    ensemble_preds = [most_common(preds) for preds in all_preds]

    ### now amend the output with predicted labels
    output_str = []
    output_lines = load_test_ref_lines()
    for i, uid in enumerate(test_uids):
        uid = test_uids[i]
        cur_out_line = output_lines[uid]

        # sanity check
        used_line = test_lines[i].split("|")
        output_line = cur_out_line.split("|")
        if (used_line[0] != output_line[0]) or (used_line[1] != output_line[1]):
            print "-- something is wrong!!!! --"
            pdb.set_trace()

        cur_out_line = cur_out_line.replace("Discourse Facet: ", "Discourse facet: %s" % ensemble_preds[i])
        output_str.append(cur_out_line)

    with open(output_file, 'wb') as outf:
        outf.write("\n".join(output_str))

    
# helper method for voting/ensemble approach
def most_common(L):
    groups = itertools.groupby(sorted(L))
    def _auxfun((item, iterable)):
        return len(list(iterable)), -L.index(item)
    return max(groups, key=_auxfun)[0]


def train_models(X_train, y_train, return_vectorizer=False):
    ### simple multinomial model
    nb_parameters = {'alpha':[.01, .1, .5, 1, 2]}
    mult_nb = GridSearchCV(MultinomialNB(), nb_parameters, scoring="accuracy")
    mult_clf = mult_nb.fit(X_train, y_train) #multinomial classifer
    
    ### SVM        
    svc = LinearSVC()
    parameters = {'C':[.0001, .001, .05, .01, .1, 1, 10, 100]}
    clf = GridSearchCV(svc, parameters, scoring='accuracy')
    svm_clf = clf.fit(X_train, y_train) #svm classifer
    
    ### random forest 
    rf= RandomForestClassifier(n_estimators=20)
    rf_clf = rf.fit(X_train.toarray(), y_train)

    ### knn
    nn_parameters = {'n_neighbors':[1,3,5,7,11,13]}
    neigh = GridSearchCV(KNeighborsClassifier(), nn_parameters, scoring="accuracy")
    neigh.fit(X_train, y_train)

    ### logistic regression (all v one)
    parameters = {'C':[.00001, .0001, .001, .05, .01, .1, 1, 10, 100]}
    log_reg = GridSearchCV(LR(), parameters, scoring="accuracy")
    log_reg_clf = log_reg.fit(X_train, y_train)

    return [mult_clf, svm_clf, rf_clf, neigh, log_reg_clf]

'''
splits on citances, rather than rows: this is more appropriate!
'''
def cv_unique_citances(stop_words=None, include_reference_texts=True):
    annotations = load_annotations()
    X, y, v = to_Xy(annotations, stop_words=stop_words, 
                    return_vectorizer=True,
                    include_reference_texts=include_reference_texts,
                    include_predicted_reference_texts=True)

    X1, y1 = to_Xy(annotations, stop_words=stop_words, vectorizer=v,
                    include_reference_texts=include_reference_texts, 
                    include_predicted_reference_texts=True)

    mult_accuracies, svm_accuracies, lr_accuracies, rf_accuracies, knn_accuracies = [], [], [], [], []
    ensemble_accuracies = []

    # this dictionary maps unique citances to lists of
    # indices which point to (potentially multiple)
    # annotations of said citances
    citance_ids_to_indices = defaultdict(list)
    for i, annotation in enumerate(annotations):
        citance_ids_to_indices[annotation.u_citance_id()].append(i)
    
    # L will bea  list of keys (unique citance ids)
    L = list(citance_ids_to_indices)
    kf = KFold(len(L), n_folds=10, shuffle=True)  
    for train, test in kf:
        train_rows = np.array([])
        test_rows = np.array([]) 

        # now iterate over the 
        for train_index in train:
            train_rows = np.concatenate((train_rows, np.array(
                    citance_ids_to_indices[L[train_index]])))
        
        for test_index in test:    
            test_rows = np.concatenate((test_rows, np.array(
                    citance_ids_to_indices[L[test_index]])))
        

        X_train, y_train = X[list(train_rows)], y[list(train_rows)]

        mult_clf, svm_clf, rf_clf, neigh, log_reg_clf = train_models(X_train, y_train)
        #pdb.set_trace()
        multinomial_preds = mult_clf.predict(X1[test_rows])
        svm_preds = svm_clf.predict(X1[test_rows])
        lr_preds = log_reg_clf.predict(X1[test_rows])
        rf_preds = rf_clf.predict(X1[test_rows].toarray())
        nn_preds = neigh.predict(X1[test_rows])

        y_test = y[list(test_rows)]
        mult_accuracy = sklearn.metrics.accuracy_score(y_test, multinomial_preds)
        mult_accuracies.append(mult_accuracy)

        svm_acc = sklearn.metrics.accuracy_score(y_test, svm_preds)
        svm_accuracies.append(svm_acc)

        lr_acc = sklearn.metrics.accuracy_score(y_test, lr_preds)
        lr_accuracies.append(lr_acc)

        nn_acc = sklearn.metrics.accuracy_score(y_test, nn_preds)
        knn_accuracies.append(nn_acc)

        rf_acc = sklearn.metrics.accuracy_score(y_test, rf_preds)
        rf_accuracies.append(rf_acc)

        all_preds = [[svm_preds[i], lr_preds[i], multinomial_preds[i], rf_preds[i], nn_preds[i]] 
                        for i in xrange(len(y_test))]
        ensemble_preds = [most_common(preds) for preds in all_preds]

        ensemble_acc = sklearn.metrics.accuracy_score(y_test, ensemble_preds)
        ensemble_accuracies.append(ensemble_acc)
        
    print "mean accuracy for multreg: %s" % np.mean(np.array(mult_accuracies))
    print "mean accuracy for svm: %s" % np.mean(np.array(svm_accuracies))
    print "mean accuracy for lr: %s" % np.mean(np.array(lr_accuracies))
    print "mean accuracy for rf: %s" % np.mean(np.array(rf_accuracies))
    print "mean accuracy for knn: %s" % np.mean(np.array(knn_accuracies))
    print "mean accuracy for ensemble: %s" % np.mean(np.array(ensemble_accuracies))
              
def cv():
    X, y = to_Xy(load_annotations())
    kf = KFold(X.shape[0], n_folds=10, shuffle=True)
    accuracies, precs, recalls, fs = [], [], [], []
    for train, test in kf:
        svc = LinearSVC()
        parameters = {'C':[.00001, .0001, .001, .05, .01, .1, 1]}
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

'''
def collapse_test_annotations(dir_="/Users/byron/dev/TAC/TAC_2014_BiomedSumm_Evaluation_Data/data"):
    for sub_dir is [d for d in os.listdir(dir_) if not d.startswith("")]:
        if sub_dir.
'''
def load_test_annotations(fname="annoLegend_eval.txt"):
    return load_annotations(fname=fname, train=False, return_lines=True)

def load_predicted_ref_texts_dict(fname="annoPrediction_all3.txt"):
    # just creat a dictionary mapping unique ids to reference texts
    #self.topic_id + "-" + self.citance_num
    u_ids_to_predicted_ref_texts = {}
    topic_id_index, citance_id_index, reference_text_index = 0, 1, 4
    with open(fname) as predictions:
        for line in predictions:
            l = line.split("|")
            topic_id = get_value(l, topic_id_index, "Topic ID:")
            citance_id = get_value(l, citance_id_index, "Citance Number:")
            uid = topic_id + "-" + citance_id
            predicted_reference_text = get_value(l, reference_text_index, "Reference Text:")
            u_ids_to_predicted_ref_texts[uid] = predicted_reference_text

    return u_ids_to_predicted_ref_texts

### just for output!
def load_test_ref_lines(fname="annoPrediction_all3.txt"):
    # just creat a dictionary mapping unique ids to reference texts
    #self.topic_id + "-" + self.citance_num
    u_ids_to_lines = {}
    topic_id_index, citance_id_index, reference_text_index = 0, 1, 4
    with open(fname) as predictions:
        for line in predictions:
            l = line.split("|")
            topic_id = get_value(l, topic_id_index, "Topic ID:")
            citance_id = get_value(l, citance_id_index, "Citance Number:")
            uid = topic_id + "-" + citance_id
            u_ids_to_lines[uid] = line
            
    return u_ids_to_lines


def load_annotations(fname="annoLegend.txt", train=True, return_lines=False):
    annotations = []

    with open(fname) as annotations_data:
        lines = annotations_data.readlines()
        annotations = [Annotation(line, train=train) for line in lines]

    if return_lines:
        return annotations, lines
    return annotations