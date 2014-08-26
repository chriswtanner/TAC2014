#!/usr/bin/python
import random
import fnmatch
import os
import re
def main():

    # custom params
    baseDir = '/Users/christanner/research/projects/TAC2014/'

    dataInputDir = baseDir + 'TAC_2014_BiomedSumm_Training_Data_V1.2/data/'
    dataOutputDir = baseDir + 'eval/'
    malletOutput = dataOutputDir + "mallet-tac.txt"
    annoOutput = dataOutputDir + "annoLegend.txt"

    # tokens which will be ignored (case sensitive)
    badTokens = []
    badTokens.append("Author Affiliations")
    badTokens.append("Abstract")
    badTokens.append("Next Section")
    badTokens.append("Previous Section")
    badTokens.append("Figure")
    badTokens.append("Table")
    badTokens.append("View Larger Version:")
    badTokens.append("In this page In a new window")
    badTokens.append("In this window In a new window")
    badTokens.append("Download as PowerPoint Slide")
    badTokens.append("View this table")
    badTokens.append("Results")
    badTokens.append("Discussion")
    badTokens.append("Methods")
    badTokens.append("Formula")
    badTokens.append("Results")
    badTokens.append("Go To:")

    fullDocs = []
    annoDocs = []
    malletOut = open(malletOutput, 'w')
    annoOut = open(annoOutput, 'w')

    # traverses dataDir to find docs and the annotations
    for root, dirnames, filenames in os.walk(dataInputDir):
        for filename in fnmatch.filter(filenames, '*.txt'):
            p = str(os.path.join(root, filename))
            
            if ("Documents_Text" in p) and ("Summary" not in p):
                fullDocs.append(p)
            elif ("Annotation" in p) and ("ann" in p):
                annoDocs.append(p)

    # reads every doc in the corpus, in order to output a mallet-formatted file:
    # e.g.,
    #     filename1 filename1 doc1's text here all on 1 line
    #     filename2 filename2 doc2's text here all on 1 line

    # NOTE: we ignore all info before the 'introduction', 'main text', and 'abstract' section titles
    for d in fullDocs:
        print "reading doc: " + d
        matchObj = re.match(r'.*data/(.*)_TRAIN.*/(.*)', d, re.M|re.I)
        if matchObj:
            fileID = matchObj.group(1) + "_" + matchObj.group(2)
        #fileID = d[d.rfind("/")+1:]
        oneline = ""
        foundIntro = False
        with open(d) as f:
            for line in f:
                line = line.strip()
                if (line.lower() in ["introduction", "abstract", "main text"]):
                    oneline = ""
                    foundIntro = True
                elif (line == "References"):
                    break
                else:
                    isBad = False
                    
                    # checks if the line contains any 'bad' tokens, which we'll ignore
                    for bad in badTokens:
                        if (line.startswith(bad)):
                            isBad = True
                            continue
                    if (not isBad):
                        oneline += (line + " ")
            oneline = oneline.strip()
            malletOut.write(fileID + " " + fileID + " " + oneline + "\n");

        if (not foundIntro):
            print "ERROR: we found no intro/abstract/maintext section, so we saved the entire text!"
    malletOut.close()

    # simply concatenates all annotation files together into 1 file
    for d in annoDocs:
        #print "concatenating " + d
        with open(d) as f:
            for line in f:
                if (line.strip() != ""):
                    annoOut.write(line)

main()
    
