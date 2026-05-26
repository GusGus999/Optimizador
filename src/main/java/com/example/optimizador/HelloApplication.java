package com.example.optimizador;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 950, 650);

        // Carga explícita del CSS para evitar problemas de empaquetado
        String css = HelloApplication.class.getResource("style.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("Optimización - Lenguajes y Autómatas II");
        stage.setScene(scene);
        stage.show();
    }
}
