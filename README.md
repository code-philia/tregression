# TRregression (ERASE)
TRregression (Trace-Based Regression ) aims to analyze execution trace to find regression bug.

![Snapshot of TRregression](/tregression/icons/screenshot.png?raw=true "Snapshot of TRregression")

A demo video is here.

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/Uu8z3ONwRqs/0.jpg)](https://www.youtube.com/watch?v=Uu8z3ONwRqs)

Audience can also check https://youtu.be/Mte08XIETlU to apply autonamous debugging with Tregression.

Please copy the `.jar` file in `tregression\tregression\lib_resource` to eclipse `dropins\junit_lib` folder `eclipse\committers-2023-062\eclipse\dropins\junit_lib`.

# Citation
If you need to reference our technique, please use the following citations:

- Haijun Wang#, Yun Lin#*, Zijiang Yang, Jun Sun, Yang Liu, Jin Song Dong, Qinghua Zhen, and Ting Liu. Explaining Regressions via Alignment Slicing and Mending, Transcation on Software Engineering (TSE 2019). (#co-first author, *corrsponding author)

# ❗Important Notice
If you are using __Eclipse 4.25 (2022-09)__ or later versions, __Java 17__ or newer JRE/JDK is required.

For older versions of Eclipse, please refer to the [Eclipse Wiki Page](https://wiki.eclipse.org/Eclipse/Installation) for more information.

# Source Code Configuration
## Dependency
The TRegression (i.e., ERASE) project relies on Microbat project to collect execution Trace of Java program. When you are importing tregression project, you need to important Microbat project (https://github.com/llmhyy/microbat) as well. Note that all the projects are Eclipse plugin project. The imported projects are listed as follows:
- microbat (mirobat)
- microbat_instrumentator (mirobat)
- microbat_junit_test (mirobat)
- mutation (mirobat)
- sav.commons (mirobat)
- tregression (tregression)

Moreover, this prototype are build on top of Defects4J bugs. We forked Defects4J repository (https://github.com/llmhyy/defects4j) and please checkout the buggy version and fixed version by our script. (https://github.com/llmhyy/defects4j/blob/master/checkout.sh). If you run the script successfully, you can checkout the bug file structure as follows:
bug_repo

|__ Chart (project_id)<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ 1 (bug_id)<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ 2   <br /> 
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ ...<br />
  
Last, please unzip this file (https://github.com/llmhyy/tregression/blob/master/tregression/dependent_lib/junit_lib.rar) under the dropins directory of your eclipse root folder. It contains all the runtime Java libraries.

## Running Tregression (ERASE) on Defects4J bugs
After import the projects, right-click the tregression project, and choose "Run As Eclipse Application", you can start debugging Tregression. You need to configure the settings in the Eclipse-application as follows:
![Snapshot of TRregression](/tregression/icons/preference_configuration.png?raw=true "Snapshot of TRregression Settings")

Second, please switch to Tregression perspective by (Windows >> Perspectives >> Open Operspective >> Other). 

Third, click "Tregression" menu >> Run for Seperate Versions. The tool will automate the regression bug detection.

## Running Tregression (ERASE) on Regs4j bugs
Note: The following set-up is not user-friendly (requires manual set-up and understanding of Defects4J folder structure) and is a work in progress.

Set up a Regs4J bug-fix in a similar folder structure as Defects4J.

|__ project-name (project_id)<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ 1 (bug_id)<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ bug<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ failing_tests<br />
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|__ fix<br />

Ensure MAVEN_HOME environment variable is set to the path to your system's maven.

Follow the steps for running on Defects4J as above.
