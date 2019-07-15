@echo off
set code="C:\Users\Andrew\Documents\Work\VERSCode\V2MetaAnalysis"
set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERSCode\V2MetaAnalysis"
rem set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% V2MetaAnalysis.V2MetaAnalysis %*
