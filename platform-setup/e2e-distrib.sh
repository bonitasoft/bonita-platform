#!/bin/sh

mvn -f ../pom.xml clean install -DskipTests

export VERSION=`cat target/classes/VERSION`

echo "========================================"
echo "version:${VERSION}"
echo "========================================"

export E2E_DIR="target/e2e-distrib"
export ZIP=Bonita-BPM-platform-setup-${VERSION}.zip

rm -rf ${E2E_DIR}
unzip -q -d ${E2E_DIR} target/${ZIP}
unzip -q -d ${E2E_DIR}-jar-exploded ${E2E_DIR}/lib/platform-setup-${VERSION}.jar

echo "========================================"
echo "platform-setup-${VERSION}.jar exploded:"
tree ${E2E_DIR}-jar-exploded
echo "========================================"

ls -l ${E2E_DIR}/../platform-setup-${VERSION}-tests.jar
unzip -q -d ${E2E_DIR}-tests-jar-exploded target/platform-setup-${VERSION}-tests.jar

echo "========================================"
echo "platform-setup-${VERSION}-test.jar exploded:"
tree ${E2E_DIR}-tests-jar-exploded
echo "========================================"

echo "========================================"
echo "distribution structure:"
echo "========================================"
tree -L 3 ${E2E_DIR}

echo "========================================"
echo "should contain readme:"
echo "========================================"
cat ${E2E_DIR}/README.md

echo "========================================"
echo "check permissions:"
echo "========================================"
ls -ltrR ${E2E_DIR}



