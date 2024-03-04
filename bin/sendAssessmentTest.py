#!/usr/bin/python

# This Python program can be used for local testing of RabbitMQ messaging between
# metadig services, assessment processing and Solr indexing.

import sys
import re
print("Sending assessment request to metadig-controller (test mode):")
print("Number of arguments: ", len(sys.argv))
import socket

host="localhost"
portNum = 33000

if(len(sys.argv) > 1):
    portNum = int(sys.argv[1])

print ("host: %s" % host)
print ("port: %d" % portNum)
clientsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
clientsocket.connect((host, portNum))

testCount = 1
suiteId = "python.suite"
nodeId = "urn:node:ARCTIC"

idVal = "doi:10.18739/A2W08WG3R"
fileIdVal = re.sub('[/\(\")]', '_', idVal)
testDir = "./src/test/resources/test-docs"
mdFile = "%s/%s.xml" % (testDir, fileIdVal)
smFile = "%s/%s.sm" % (testDir, fileIdVal)

# First send the type of request, either 'graph' or 'quality'
clientsocket.send('quality\n'.encode('utf-8'))

# Next send the number of tests that will be run
clientsocket.send(f"{testCount}\n".encode('utf-8'))

msg = '%s,%s,%s,%s,%s\n' % (idVal, mdFile, smFile, suiteId, nodeId)

print(msg)
clientsocket.send(msg.encode('utf-8'))

# Server will stop reading from the current port connection
clientsocket.send("Done\n".encode('utf-8'))
# Server will stop reading from any port connection, but will continue to run
clientsocket.close()
