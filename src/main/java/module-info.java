module com.example.optimizador {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.optimizador to javafx.fxml;
    exports com.example.optimizador;
}