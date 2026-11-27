package co.edu.udea.arqsoft.inventario.error;

public class RecursoNoEncontradoException extends RuntimeException {

    private final String codigo;

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public static RecursoNoEncontradoException sede(Long sedeId) {
        return new RecursoNoEncontradoException("SEDE_NO_ENCONTRADA", "No existe una sede con id " + sedeId);
    }

    public static RecursoNoEncontradoException producto(Long id) {
        return new RecursoNoEncontradoException("PRODUCTO_NO_ENCONTRADO", "No existe un producto con id " + id);
    }

    public String getCodigo() {
        return codigo;
    }
}
