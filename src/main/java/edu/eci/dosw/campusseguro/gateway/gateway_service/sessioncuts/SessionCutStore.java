package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Guarda en memoria los últimos cortes de sesión publicados por MS01 y decide si un token quedó cortado
 */
@Component
public class SessionCutStore {

    private volatile SessionCutSnapshot current = SessionCutSnapshot.EMPTY;

    /**
     * Reemplaza por completo los cortes conocidos con una instantánea nueva
     *
     * @param snapshot instantánea recién consultada a MS01
     */
    public void replace(SessionCutSnapshot snapshot) {
        this.current = snapshot;
    }

    /**
     * Indica si un token quedó cortado por un cambio de acceso posterior a su emisión
     *
     * @param userId id del usuario del token
     * @param roles roles del token
     * @param issuedAt instante de emisión del token
     * @return true si el corte del usuario o el de alguno de sus roles es estrictamente posterior a la emisión
     */
    public boolean isCut(String userId, List<String> roles, Instant issuedAt) {
        SessionCutSnapshot snapshot = current;
        Instant userCut = snapshot.users().get(userId.toLowerCase(Locale.ROOT));
        if (userCut != null && issuedAt.isBefore(userCut)) {
            return true;
        }
        for (String role : roles) {
            Instant roleCut = snapshot.roles().get(role);
            if (roleCut != null && issuedAt.isBefore(roleCut)) {
                return true;
            }
        }
        return false;
    }
}
