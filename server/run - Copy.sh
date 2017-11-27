#!/bin/bash
java -Djava.util.logging.config.file=logging.properties -jar router.jar $1 localhost 8887 1000

