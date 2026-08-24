package com.acj.firma.acjfirmalocal;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/acj/firma/acjfirmalocal/firma-main.fxml")
        );

        Parent root = loader.load();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Visualización de FXML");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
