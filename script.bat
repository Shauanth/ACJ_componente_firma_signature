@echo off
cd /d "%~dp0"
java --module-path "%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\17.0.6\javafx-controls-17.0.6.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\17.0.6\javafx-graphics-17.0.6.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\17.0.6\javafx-base-17.0.6.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\17.0.6\javafx-fxml-17.0.6.jar;%USERPROFILE%\.m2\repository\org\openjfx\javafx-web\17.0.6\javafx-web-17.0.6.jar" --add-modules javafx.controls,javafx.fxml,javafx.web -jar acj-firma-local-1.0-SNAPSHOT.jar %*
pause