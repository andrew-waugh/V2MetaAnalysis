@echo off
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
) else (
	set code="C:/Users/Andrew/Documents/Work/VERSCode"
)
java -classpath %code%/V2MetaAnalysis/dist/* V2MetaAnalysis.V2MetaAnalysis %*
