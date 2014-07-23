#!/usr/bin/python
import random
import fnmatch
import os

def main():
    baseDir = '/Users/christanner/research/projects/TAC2014/'

    dataInputDir = baseDir + 'TAC_2014_BiomedSumm_Training_Data/data/'
    dataOutputDir = baseDir + 'eval/'
    malletOutput = dataOutputDir + "mallet-tac.txt"
    docLegendOutput = dataOutputDir + "tacLegend.txt"

    badTokens = []
    badTokens.append("author affiliations")
    badTokens.append("abstract")
    badTokens.append("next section")
    badTokens.append("previous section")
    badTokens.append("figure")
    badTokens.append("table")
    badTokens.append("view larger version:")
    badTokens.append("in this page in a new window")
    badTokens.append("in this window in a new window")
    badTokens.append("download as powerpoint slide")
    badTokens.append("view this table")
    badTokens.append("results")
    badTokens.append("discussion")
    badTokens.append("methods")
    badTokens.append("formula")
    badTokens.append("results")
    badTokens.append("introduction")
    badTokens.append("go to:")
    fullDocs = []
    malletOut = open(malletOutput, 'w')

    for root, dirnames, filenames in os.walk(dataInputDir):
        for filename in fnmatch.filter(filenames, '*.txt'):
            p = str(os.path.join(root, filename))
            
            if ("Documents_Text" in p) and ("Summary" not in p):
                fullDocs.append(p)


    # reads every doc in the corpus, in order to output a mallet-formatted file:
    # e.g.,
    #     filename1 filename1 doc1's text here all on 1 line
    #     filename2 filename2 doc2's text here all on 1 line

    # NOTE: we ignore all info before the 'introduction', 'main text', and 'abstract' section titles
    for d in fullDocs:
        print "reading doc: " + d
        fileID = d[d.rfind("/")+1:]
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

main()
    
