package nova.mjs.domain.thingo.block.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 자기 자신을 차단하려 할 때 발생.
 */
public class SelfBlockNotAllowedException extends BlockException {

    public SelfBlockNotAllowedException() {
        super(ErrorCode.BLOCK_SELF_NOT_ALLOWED);
    }
}
