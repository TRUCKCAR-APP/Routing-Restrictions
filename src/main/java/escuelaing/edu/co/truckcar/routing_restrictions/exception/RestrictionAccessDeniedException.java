package escuelaing.edu.co.truckcar.routing_restrictions.exception;

/** El usuario no puede modificar restricciones viales: 401 sin rol, 403 si su rol no es de la torre de control. */
public class RestrictionAccessDeniedException extends RuntimeException {

    private final boolean unauthenticated;

    public RestrictionAccessDeniedException(String message, boolean unauthenticated) {
        super(message);
        this.unauthenticated = unauthenticated;
    }

    public boolean isUnauthenticated() {
        return unauthenticated;
    }
}
