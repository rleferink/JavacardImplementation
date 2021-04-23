# Path to the Java Card Development Kit
JC_HOME=util/java_card_kit-2_2_1

# Version of JCardSim to use;
JCARDSIM=jcardsim-3.0.4-SNAPSHOT

# Beware that only JCardSim-3.0.4-SNAPSHOT.jar includes the classes
# AIDUtil and CardTerminalSimulator, so some of the code samples on
# https://jcardsim.org/docs do not work with older versions
#    JCARDSIM=jcardsim-2.2.1-all
#    JCARDSIM=jcardsim-2.2.2-all

# Classpath for JavaCard code, ie the smartcard applet; this includes
# way more than is probably needed
JC_CLASSPATH=${JC_HOME}/lib/apdutool.jar:${JC_HOME}/lib/apduio.jar:${JC_HOME}/lib/converter.jar:${JC_HOME}/lib/jcwde.jar:${JC_HOME}/lib/scriptgen.jar:${JC_HOME}/lib/offcardverifier.jar:${JC_HOME}/lib/api.jar:${JC_HOME}/lib/installer.jar:${JC_HOME}/lib/capdump.jar:${JC_HOME}/samples/classes:${CLASSPATH}

all: applet terminal quicktest

applet: CalcApplet/bin/CalcApplet.class 

CalcApplet/bin/CalcApplet.class: CalcApplet/src/applet/CalcApplet.java
	javac -d CalcApplet/bin -cp ${JC_CLASSPATH}:CalcTerminal/src CalcApplet/src/applet/CalcApplet.java 

quicktest: CalcTerminal/bin/terminal/QuickTest.class

CalcTerminal/bin/terminal/QuickTest.class: CalcTerminal/src/terminal/QuickTest.java
	javac -d CalcTerminal/bin -cp ${JC_HOME}:util/jcardsim/${JCARDSIM}.jar:CalcApplet/bin CalcTerminal/src/terminal/QuickTest.java

runquicktest: 
	# Sends some sample APDUs to the CalcApplet
	java -cp util/jcardsim/${JCARDSIM}.jar:CalcTerminal/bin:CalcApplet/bin terminal.QuickTest

terminal: CalcTerminal/bin/terminal/CalcTerminal.class

CalcTerminal/bin/terminal/CalcTerminal.class: CalcTerminal/src/terminal/CalcTerminal.java
	javac -d CalcTerminal/bin -cp ${JC_HOME}:util/jcardsim/${JCARDSIM}.jar:CalcApplet/bin:CalcTerminal/bin CalcTerminal/src/terminal/CalcTerminal.java  

runterminal: 
	# Runs the GUI terminal
	java -cp util/jcardsim/${JCARDSIM}.jar:CalcTerminal/bin:CalcApplet/bin terminal.CalcTerminal

clean:
	rm -rf CalcApplet/bin/*  
	rm -rf CalcTerminal/bin/*
