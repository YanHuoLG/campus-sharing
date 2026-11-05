package cn.edu.campus.sharing.reservation;

import cn.edu.campus.sharing.common.BusinessException;
import org.springframework.http.HttpStatus;

/** Only for expected failures whose attempt counter or expiry transition must commit. */
public class CommittedReservationException extends BusinessException {
    public CommittedReservationException(String message) { super(HttpStatus.CONFLICT, message); }
}
