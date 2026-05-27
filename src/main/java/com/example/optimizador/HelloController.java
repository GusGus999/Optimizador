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
        boolean huboCambios = true;

        while (huboCambios) {
            String estadoAnterior = obtenerCodigoTexto(instrucciones);

            instrucciones = eliminarSubexpresionesComunes(instrucciones);
            instrucciones = propagarCopias(instrucciones);
            instrucciones = eliminarCodigoMuerto(instrucciones);
            instrucciones = calculoPrevioConstantes(instrucciones);
            instrucciones = transformacionesAlgebraicas(instrucciones);
            instrucciones = reduccionIntensidad(instrucciones);

            String estadoNuevo = obtenerCodigoTexto(instrucciones);
            huboCambios = !estadoAnterior.equals(estadoNuevo);
        }

        mostrarResultado(instrucciones);
    }
    private List<Instruccion> parsear(String codigo) {
        List<Instruccion> lista = new ArrayList<>();
        String[] lineas = codigo.split("\n");

        Pattern pBinaria = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*([a-zA-Z0-9_\\.]+)\\s*([\\+\\-\\*\\/\\^])\\s*([a-zA-Z0-9_\\.]+)$");
        Pattern pArreglo = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*([a-zA-Z0-9_]+)\\s*\\[\\s*([a-zA-Z0-9_]+)\\s*\\]$");
        Pattern pAsignacion = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*([a-zA-Z0-9_\\.]+)$");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            Matcher mBin = pBinaria.matcher(linea);
            Matcher mArr = pArreglo.matcher(linea);
            Matcher mAsig = pAsignacion.matcher(linea);

            if (mBin.matches()) {
                lista.add(new Instruccion(mBin.group(1), mBin.group(2), mBin.group(3), mBin.group(4)));
            } else if (mArr.matches()) {
                lista.add(new Instruccion(mArr.group(1), mArr.group(2), "[]", mArr.group(3)));
            } else if (mAsig.matches()) {
                lista.add(new Instruccion(mAsig.group(1), mAsig.group(2), null, null));
            } else {
                lista.add(new Instruccion(linea));
            }
        }
        return lista;
    }

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

    private List<Instruccion> propagarCopias(List<Instruccion> instrucciones) {
        Map<String, String> copias = new HashMap<>();
        for (Instruccion inst : instrucciones) {
            if (inst.arg1 != null && copias.containsKey(inst.arg1)) {
                inst.arg1 = copias.get(inst.arg1);
            }
            if (inst.arg2 != null && copias.containsKey(inst.arg2)) {
                inst.arg2 = copias.get(inst.arg2);
            }
            if (inst.op == null && inst.arg1 != null && inst.res != null) {
                copias.put(inst.res, inst.arg1);
            }
        }
        return instrucciones;
    }

    private List<Instruccion> eliminarCodigoMuerto(List<Instruccion> instrucciones) {
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            Set<String> usadas = new HashSet<>();

            if (!instrucciones.isEmpty()) {
                Instruccion ultima = instrucciones.get(instrucciones.size() - 1);
                if (ultima.res != null) usadas.add(ultima.res);
                if (ultima.textoOriginal != null) usadas.add(ultima.textoOriginal.trim());
            }

            for (int i = instrucciones.size() - 1; i >= 0; i--) {
                Instruccion inst = instrucciones.get(i);

                if (inst.textoOriginal != null) continue;

                if (inst.res != null && !usadas.contains(inst.res)) {
                    instrucciones.remove(i);
                    cambio = true;
                    continue;
                }

                if (inst.arg1 != null && !isNumeric(inst.arg1)) usadas.add(inst.arg1);
                if (inst.arg2 != null && !isNumeric(inst.arg2)) usadas.add(inst.arg2);
            }
        }
        return instrucciones;
    }

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

    private List<Instruccion> transformacionesAlgebraicas(List<Instruccion> instrucciones) {
        for (Instruccion inst : instrucciones) {
            if (inst.op != null) {
                if (inst.op.equals("+")) {
                    if ("0".equals(inst.arg1)) {
                        inst.arg1 = inst.arg2;
                        inst.op = null;
                        inst.arg2 = null;
                    } else if ("0".equals(inst.arg2)) {
                        inst.op = null;
                        inst.arg2 = null;
                    }
                } else if (inst.op.equals("-")) {
                    if ("0".equals(inst.arg2)) {
                        inst.op = null;
                        inst.arg2 = null;
                    } else if (inst.arg1.equals(inst.arg2)) {
                        inst.arg1 = "0"; inst.op = null;
                        inst.arg2 = null;
                    }
                } else if (inst.op.equals("*")) {
                    if ("1".equals(inst.arg1)) {
                        inst.arg1 = inst.arg2;
                        inst.op = null;
                        inst.arg2 = null;
                    } else if ("1".equals(inst.arg2)) {
                        inst.op = null;
                        inst.arg2 = null;
                    } else if ("0".equals(inst.arg1) || "0".equals(inst.arg2)) {
                        inst.arg1 = "0";
                        inst.op = null;
                        inst.arg2 = null;
                    }
                } else if (inst.op.equals("/")) {
                    if ("1".equals(inst.arg2)) {
                        inst.op = null;
                        inst.arg2 = null;
                    } else if (inst.arg1.equals(inst.arg2) && !inst.arg1.equals("0")) {
                        inst.arg1 = "1";
                        inst.op = null;
                        inst.arg2 = null;
                    }
                }
            }
        }
        return instrucciones;
    }

    private List<Instruccion> reduccionIntensidad(List<Instruccion> instrucciones) {
        for (Instruccion inst : instrucciones) {
            if (inst.op != null) {
                if (inst.op.equals("*")) {
                    if ("2".equals(inst.arg2)) { // x*2 -> x+x
                        inst.op = "+";
                        inst.arg2 = inst.arg1;
                    } else if ("2".equals(inst.arg1)) { // 2*x -> x+x
                        inst.arg1 = inst.arg2;
                        inst.op = "+";
                    }
                } else if (inst.op.equals("^")) { // a^2 -> a*a
                    if ("2".equals(inst.arg2)) {
                        inst.op = "*";
                        inst.arg2 = inst.arg1;
                    }
                }
            }
        }
        return instrucciones;
    }

    private String obtenerCodigoTexto(List<Instruccion> instrucciones) {
        StringBuilder sb = new StringBuilder();
        for (Instruccion inst : instrucciones) {
            sb.append(inst.toString()).append("\n");
        }
        return sb.toString();
    }

    private void mostrarResultado(List<Instruccion> instrucciones) {
        txa_resultado.setText(obtenerCodigoTexto(instrucciones));
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
            if ("[]".equals(op)) return res + "=" + arg1 + "[" + arg2 + "]";
            if (op == null) return res + "=" + arg1;
            return res + "=" + arg1 + op + arg2;
        }
    }
}