FAT_JAR_BASE_NAME=mainModule-1.0-shaded.jar
FAT_JAR_FINAL_NAME=dpso.jar

# Build the jar.
mvn clean package

# Transferring the fat jar.
cp -R target/$FAT_JAR_BASE_NAME $SPARK_HOME/jars/

# Renaming the fat jar.
mv $SPARK_HOME/jars/$FAT_JAR_BASE_NAME $SPARK_HOME/jars/$FAT_JAR_FINAL_NAME

# Building image.
$SPARK_HOME/bin/docker-image-tool.sh -r riccardobusetti -t v1.0 build

# Pushing image.
$SPARK_HOME/bin/docker-image-tool.sh -r riccardobusetti -t v1.0 push