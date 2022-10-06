#!/bin/bash

# Description:
# Clones working and RIC commits from each project in regs4j to your specified directory, then runs maven test-compile on each of them
# Compilation failures, checkout failures, and error messages are recorded on your specified CSV file

# Usage:
# 1. Update the configuration below to match your system's.
# 2. Run the command: <this script> <path to regs4j's CLI.sh>. If not specified, "./CLI.sh" is used.
# e.g. ./regs4j.sh ./CLI.sh

# ====================== Configuration =====================
# Change the paths below to your system's
repoDirToPasteTo="/mnt/c/Users/Chenghin/Desktop/VBox-Shared/reg4j"
reverse=0
csvFile="/mnt/c/Users/Chenghin/Desktop/VBox-Shared/regs4j.csv"
# ==========================================================

firstLineInCSV="Project,Type,BugId,Regs4jError,ErrorMsg"
if [[ -f $csvFile ]]
then
    echo $firstLineInCSV > $csvFile
fi

# If path to regs4j shell script not provided, use the default
if [ -z $1 ]
then
    set -- "./CLI.sh"
fi

cliDir="$(dirname $1)"
cd $cliDir
echo "entered $cliDir directory"

cliCommand="./$(basename $1)"
echo running $cliCommand
echo ' '

testFileName="tests.txt"

projects=$(echo projects | "$cliCommand")
projects=${projects/*Done}
projects=${projects/RegMiner*}
projects=( $(echo $projects))
if [ $reverse -eq 1 ]
then
	min=0
	max=$((${#projects[@]} - 1))
	while [[ min -lt max ]]
	do
		x="${projects[$min]}"
		projects[$min]="${projects[$max]}"
		projects[$max]="$x"
		(( min++, max--))
	done
fi
echo "Printing projects..."
echo ' '
for project in ${projects[@]}
do
	echo $project
done
echo ' '
echo Starting cloning...
echo ' '
for project in ${projects[@]}
do
	echo using project $project
	numOfRegs=$(echo "use $project" | "$cliCommand")
	numOfRegs=${numOfRegs/*"regressions... "}
	numOfRegs=${numOfRegs/' '*}
	echo num of regressions: $numOfRegs
	listOutput=$({ echo "use $project"; echo "list"; } | "$cliCommand")
    tests=()
    testCaseStr=" testcase: "
    for fragment in $listOutput; do
        if [[ "$fragment" =~ .*"#".* ]]; then
            tests+=($fragment)
        fi
    done
	for j in $(seq 1 $numOfRegs)
	do
		strRepWork=$project/$j/work
		strRepRIC=$project/$j/ric
		csvRowWork=$project,work,$j,
		csvRowRIC=$project,ric,$j,
		if [[ $prevCSVContents == *"$csvRowWork"* && $prevCSVContents == *"$csvRowRIC"* ]]
		then
		    echo $project with bug id $j already recorded in csv file, skipping...
		    continue
		fi
			echo checking out $j for $project
			checkoutResult=$({ echo "use $project"; echo "checkout $j"; } | "$cliCommand")
		if [[ $checkoutResult == *"Please specify a project before checking out a bug"* ]]
		then
		    repoNotFoundMsg="Repository not found"
		    echo $repoNotFoundMsg 
		    csvRowWork+=TRUE,$repoNotFoundMsg
		    csvRowRIC+=TRUE,$repoNotFoundMsg
		    echo $csvRowWork >> $csvFile
		    echo $csvRowRIC >> $csvFile
		    continue
		fi
		pathToWorking=${checkoutResult/*"work directory:"}
		pathToWorking=${pathToWorking/work*/work}
		pathToRIC=${checkoutResult/*"ric directory:"}
		pathToRIC=${pathToRIC/ric*/ric}
		echo path to work: $pathToWorking
		echo path to ric: $pathToRIC

		projectDir=${project/\//_}

		newPath=$repoDirToPasteTo/$projectDir/$j
		mkdir -p $newPath

		echo copying working to $newPath/work
		cp -r $pathToWorking $newPath

		echo copying ric to $newPath/ric
		cp -r $pathToRIC $newPath

		testCase="${tests[$((j-1))]}"
		testCaseRunningStr='Tests run'
		testFailStr='Failures: 1'
		testPassStr='Failures: 0'
		echo compiling working commit
		mvnOutput=$( mvn test -Dtest=$testCase --file $newPath/work/pom.xml | tee /dev/fd/2 )
		mvnOutput=$( echo "$mvnOutput" | tr '\n' '^'  | tr -d '\r' ) # Replace new lines with another char, since it creates another row in csv
		if [[ $mvnOutput == *"$testCaseRunningStr"* ]]
		then
		    echo "Test case ran"
		    if [[ $mvnOutput == *"$testPassStr"* ]]
		    then
			csvRowWork+=FALSE,\"$mvnOutput\" # Add quotes so that inner commas are not used to create columns in csv
		    else
			echo "Test case failed for work"
			csvRowWork+=TRUE,\"$mvnOutput\"
		    fi
		else
		    echo "mvn failure for work"
		    csvRowWork+=TRUE,\"$mvnOutput\"
		fi
		echo $csvRowWork >> $csvFile

		echo compiling ric commit
		mvnOutput=$( mvn test -Dtest=$testCase --file $newPath/ric/pom.xml | tee /dev/fd/2 )
		mvnOutput=$( echo "$mvnOutput" | tr '\n' '^' | tr -d '\r') 
		if [[ $mvnOutput == *"$testCaseRunningStr"* ]]
		then
		    echo "Test case ran"
		    if [[ $mvnOutput == *"$testFailStr"* ]]
		    then
			csvRowRIC+=FALSE,\"$mvnOutput\" # Add quotes so that inner commas are not used to create columns in csv
		    else
			echo "Test case passed for ric"
			csvRowRIC+=TRUE,\"$mvnOutput\"
		    fi
		else
		    echo "mvn failure for ric"
		    csvRowRIC+=TRUE,\"$mvnOutput\"
		fi
		echo $csvRowRIC >> $csvFile

		echo "${tests[$((j-1))]}" > $newPath/$testFileName
	done
done
