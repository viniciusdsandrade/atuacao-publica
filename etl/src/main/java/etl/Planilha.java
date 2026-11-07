package etl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Leitura mínima de XLSX: a primeira planilha, linha a linha, com o texto de cada célula pela coluna (A, B, C...). */
public final class Planilha {

    private static final String COMPARTILHADOS = "xl/sharedStrings.xml";
    private static final String PRIMEIRA = "xl/worksheets/sheet1.xml";

    private Planilha() {
    }

    /** Linhas da primeira planilha, na ordem do arquivo. Célula vazia não entra no mapa da linha. */
    public static List<Map<String, String>> linhas(byte[] xlsx) throws IOException {
        Map<String, byte[]> partes = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (entrada.getName().equals(COMPARTILHADOS) || entrada.getName().equals(PRIMEIRA)) {
                    partes.put(entrada.getName(), zip.readAllBytes());
                }
            }
        }
        if (!partes.containsKey(PRIMEIRA)) {
            throw new IOException("XLSX sem a primeira planilha");
        }
        try {
            return celulas(partes.get(PRIMEIRA), textosCompartilhados(partes.get(COMPARTILHADOS)));
        } catch (XMLStreamException e) {
            throw new IOException("XLSX ilegível: " + e.getMessage(), e);
        }
    }

    private static XMLStreamReader leitor(byte[] xml) throws XMLStreamException {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        // Arquivo de terceiros: sem DTD nem entidade externa.
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return fabrica.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    private static List<String> textosCompartilhados(byte[] xml) throws XMLStreamException {
        List<String> textos = new ArrayList<>();
        if (xml == null) {
            return textos;
        }
        XMLStreamReader r = leitor(xml);
        StringBuilder atual = null;
        boolean dentroDeT = false;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                if (r.getLocalName().equals("si")) {
                    atual = new StringBuilder();
                } else if (r.getLocalName().equals("t")) {
                    dentroDeT = true;
                }
            } else if (evento == XMLStreamConstants.CHARACTERS && dentroDeT && atual != null) {
                atual.append(r.getText());
            } else if (evento == XMLStreamConstants.END_ELEMENT) {
                if (r.getLocalName().equals("t")) {
                    dentroDeT = false;
                } else if (r.getLocalName().equals("si") && atual != null) {
                    textos.add(atual.toString());
                    atual = null;
                }
            }
        }
        return textos;
    }

    private static List<Map<String, String>> celulas(byte[] xml, List<String> compartilhados) throws XMLStreamException {
        List<Map<String, String>> linhas = new ArrayList<>();
        XMLStreamReader r = leitor(xml);
        Map<String, String> linha = null;
        String coluna = null;
        String tipo = null;
        StringBuilder valor = null;
        boolean dentroDeValor = false;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> linha = new LinkedHashMap<>();
                    case "c" -> {
                        String referencia = r.getAttributeValue(null, "r");
                        coluna = referencia == null ? null : referencia.replaceAll("[0-9]", "");
                        tipo = r.getAttributeValue(null, "t");
                        valor = new StringBuilder();
                    }
                    case "v", "t" -> dentroDeValor = valor != null;
                    default -> {
                    }
                }
            } else if (evento == XMLStreamConstants.CHARACTERS && dentroDeValor) {
                valor.append(r.getText());
            } else if (evento == XMLStreamConstants.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "v", "t" -> dentroDeValor = false;
                    case "c" -> {
                        String texto = valor == null ? "" : valor.toString();
                        if ("s".equals(tipo) && !texto.isBlank()) {
                            int indice = Integer.parseInt(texto.strip());
                            texto = indice < compartilhados.size() ? compartilhados.get(indice) : "";
                        }
                        if (linha != null && coluna != null && !texto.isBlank()) {
                            linha.put(coluna, texto.strip());
                        }
                        valor = null;
                    }
                    case "row" -> {
                        if (linha != null) {
                            linhas.add(linha);
                        }
                        linha = null;
                    }
                    default -> {
                    }
                }
            }
        }
        return linhas;
    }
}
