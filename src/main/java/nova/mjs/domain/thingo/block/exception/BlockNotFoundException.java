package nova.mjs.domain.thingo.block.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 차단 해제 대상 관계가 존재하지 않을 때 발생.
 */
public class BlockNotFoundException extends BlockException {

    public BlockNotFoundException() {
        super(ErrorCode.BLOCK_NOT_FOUND);
    }
}
