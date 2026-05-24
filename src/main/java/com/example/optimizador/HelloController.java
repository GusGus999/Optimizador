package com.example.optimizador;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import java.util.*;
import java.util.regex.*;

public class HelloController {

    @FXML private TextArea txa_codigo;
    @FXML private TextArea txa_resultado;
    @FXML private Button btn_optimizar;
    @FXML private Button btn_limpiar;

    @FXML
    public void initialize() {
        btn_optimizar.setOnAction(e -> optimizar());
        btn_limpiar.setOnAction(e -> limpiar());
    }

    private void limpiar() {
        txa_codigo.clear();
        txa_resultado.clear();
    }

    private void optimizar() {
        String codigo = txa_codigo.getText();
        if (codigo == null || codigo.trim().isEmpty()) return;

        List<Instruccion> instrucciones = parsear(codigo);

        // Aplicación secuencial de las optimizaciones locales según el documento:
        instrucciones = eliminarSubexpresionesComunes(instrucciones); // [cite: 99, 100]
        instrucciones = propagarCopias(instrucciones);                // [cite: 105, 106]
        instrucciones = eliminarCodigoMuerto(instrucciones);          // [cite: 114, 115]
        instrucciones = calculoPrevioConstantes(instrucciones);       // [cite: 132, 133]
        instrucciones = transformacionesAlgebraicas(instrucciones);   // [cite: 146, 147]
        instrucciones = reduccionIntensidad(instrucciones);           // [cite: 162, 163]
        instrucciones = eliminarCodigoMuerto(instrucciones);          // Limpieza final de temporales sin uso

        mostrarResultado(instrucciones);
    }

    private List<Instruccion> parsear(String codigo) {
        List<Instruccion> lista = new ArrayList<>();
        String[] lineas = codigo.split("\n");

        // Expresión regular para capturar código de 3 direcciones (ej. t1 = a + b o t1 = 2)
        // Ignora los símbolos '$' por si se copia directamente del PDF [cite: 102]
        Pattern pattern = Pattern.compile("^\\$?([a-zA-Z0-9_]+)\\s*=\\s*\\$?([a-zA-Z0-9_\\.]+)\\s*(?:([\\+\\-\\*\\/\\^])\\s*\\$?([a-zA-Z0-9_\\.]+))?\\$?$");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            Matcher m = pattern.matcher(linea);
            if (m.matches()) {
                lista.add(new Instruccion(m.group(1), m.group(2), m.group(3), m.group(4)));
            } else {
                lista.add(new Instruccion(linea)); // Si no es una instrucción estándar, se pasa tal cual
            }
        }
        return lista;
    }

    // 7.8.1 Eliminación de subexpresiones comunes [cite: 99]
    private List<Instruccion> eliminarSubexpresionesComunes(List<Instruccion> instrucciones) {
        Map<String, String> expresiones = new HashMap<>();
        for (Instruccion inst : instrucciones) {
            if (inst.op != null) {
                String expr = inst.arg1 + inst.op + inst.arg2;
                String exprConmutativa = inst.arg2 + inst.op + inst.arg1;

                if (expresiones.containsKey(expr)) {
                    inst.arg1 = expresiones.get(expr);
                    inst.op = null;
                    inst.arg2 = null;
                } else if ((inst.op.equals("+") || inst.op.equals("*")) && expresiones.containsKey(exprConmutativa)) {
                    inst.arg1 = expresiones.get(exprConmutativa);
                    inst.op = null;
                    inst.arg2 = null;
                } else {
                    expresiones.put(expr, inst.res);
                }
            }
        }
        return instrucciones;
    }

    // 7.8.2 Propagación de copias [cite: 105]
    private List<Instruccion> propagarCopias(List<Instruccion> instrucciones) {
        Map<String, String> copias = new HashMap<>();
        for (Instruccion inst : instrucciones) {
            if (inst.arg1 != null && copias.containsKey(inst.arg1)) {
                inst.arg1 = copias.get(inst.arg1);
            }
            if (inst.arg2 != null && copias.containsKey(inst.arg2)) {
                inst.arg2 = copias.get(inst.arg2);
            }
            // Registrar asignación directa (x = y) o constante (t1 = 2)
            if (inst.op == null && inst.arg1 != null && inst.res != null) {
                copias.put(inst.res, inst.arg1);
            }
        }
        return instrucciones;
    }

    // 7.8.3 Eliminación de código muerto [cite: 114]
    private List<Instruccion> eliminarCodigoMuerto(List<Instruccion> instrucciones) {
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            Set<String> usadas = new HashSet<>();

            for (Instruccion inst : instrucciones) {
                if (inst.arg1 != null && !isNumeric(inst.arg1)) usadas.add(inst.arg1);
                if (inst.arg2 != null && !isNumeric(inst.arg2)) usadas.add(inst.arg2);
            }

            Iterator<Instruccion> it = instrucciones.iterator();
            while (it.hasNext()) {
                Instruccion inst = it.next();
                // Eliminamos temporales (variables que inician con 't') que nunca son referenciados [cite: 115, 116]
                if (inst.res != null && inst.res.startsWith("t") && !usadas.contains(inst.res)) {
                    it.remove();
                    cambio = true;
                }
            }
        }
        return instrucciones;
    }

    // 7.8.4 Cálculo previo de constantes [cite: 132]
    private List<Instruccion> calculoPrevioConstantes(List<Instruccion> instrucciones) {
        for (Instruccion inst : instrucciones) {
            if (inst.op != null && isNumeric(inst.arg1) && isNumeric(inst.arg2)) {
                double v1 = Double.parseDouble(inst.arg1);
                double v2 = Double.parseDouble(inst.arg2);
                double res = 0;
                boolean evaluado = true;

                switch (inst.op) {
                    case "+": res = v1 + v2; break;
                    case "-": res = v1 - v2; break;
                    case "*": res = v1 * v2; break;
                    case "/":
                        if (v2 != 0) res = v1 / v2;
                        else evaluado = false;
                        break;
                    default: evaluado = false;
                }

                if (evaluado) {
                    // Truncar a entero si es exacto (ej. 4-2=2) [cite: 135]
                    if (res == Math.floor(res)) {
                        inst.arg1 = String.valueOf((int) res);
                    } else {
                        inst.arg1 = String.valueOf(res);
                    }
                    inst.op = null;
                    inst.arg2 = null;
                }
            }
        }
        return instrucciones;
    }

    // Transformaciones algebraicas [cite: 146]
    private List<Instruccion> transformacionesAlgebraicas(List<Instruccion> instrucciones) {
        for (Instruccion inst : instrucciones) {
            if (inst.op != null) {
                if (inst.op.equals("+")) {
                    if ("0".equals(inst.arg1)) { inst.arg1 = inst.arg2; inst.op = null; inst.arg2 = null; } // 0+x=x [cite: 148]
                    else if ("0".equals(inst.arg2)) { inst.op = null; inst.arg2 = null; } // x+0=x [cite: 148]
                } else if (inst.op.equals("-")) {
                    if ("0".equals(inst.arg2)) { inst.op = null; inst.arg2 = null; } // x-0=x [cite: 148]
                    else if (inst.arg1.equals(inst.arg2)) { inst.arg1 = "0"; inst.op = null; inst.arg2 = null; }
                } else if (inst.op.equals("*")) {
                    if ("1".equals(inst.arg1)) { inst.arg1 = inst.arg2; inst.op = null; inst.arg2 = null; } // 1*x=x [cite: 149]
                    else if ("1".equals(inst.arg2)) { inst.op = null; inst.arg2 = null; } // x*1=x [cite: 149]
                    else if ("0".equals(inst.arg1) || "0".equals(inst.arg2)) { inst.arg1 = "0"; inst.op = null; inst.arg2 = null; }
                } else if (inst.op.equals("/")) {
                    if ("1".equals(inst.arg2)) { inst.op = null; inst.arg2 = null; } // x/1=x [cite: 149]
                }
            }
        }
        return instrucciones;
    }

    // 7.8.4 Reducción de intensidad [cite: 162]
    private List<Instruccion> reduccionIntensidad(List<Instruccion> instrucciones) {
        for (Instruccion inst : instrucciones) {
            if (inst.op != null) {
                if (inst.op.equals("*")) {
                    if ("2".equals(inst.arg2)) { // x*2 -> x+x [cite: 167]
                        inst.op = "+";
                        inst.arg2 = inst.arg1;
                    } else if ("2".equals(inst.arg1)) { // 2*x -> x+x [cite: 167]
                        inst.arg1 = inst.arg2;
                        inst.op = "+";
                    }
                } else if (inst.op.equals("^")) { // x^2 -> x*x [cite: 167]
                    if ("2".equals(inst.arg2)) {
                        inst.op = "*";
                        inst.arg2 = inst.arg1;
                    }
                }
            }
        }
        return instrucciones;
    }

    private void mostrarResultado(List<Instruccion> instrucciones) {
        StringBuilder sb = new StringBuilder();
        for (Instruccion inst : instrucciones) {
            sb.append(inst.toString()).append("\n");
        }
        txa_resultado.setText(sb.toString());
    }

    private boolean isNumeric(String str) {
        if (str == null) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Clase auxiliar para gestionar internamente el código de 3 direcciones
    class Instruccion {
        String res;
        String arg1;
        String op;
        String arg2;
        String textoOriginal;

        public Instruccion(String res, String arg1, String op, String arg2) {
            this.res = res;
            this.arg1 = arg1;
            this.op = op;
            this.arg2 = arg2;
        }

        public Instruccion(String textoOriginal) {
            this.textoOriginal = textoOriginal;
        }

        @Override
        public String toString() {
            if (textoOriginal != null) return textoOriginal;
            if (op == null) return res + " = " + arg1;
            return res + " = " + arg1 + " " + op + " " + arg2;
        }
    }
}