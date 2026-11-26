package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.FinanceTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FinanceTransactionMapper extends BaseMapper<FinanceTransaction> {

    /** 锁住流水行：同一流水号的重复回调串行执行，第二次看到的已是终态，从而不会重复扣款 */
    @Select("SELECT * FROM finance_transactions WHERE serial_no = #{serialNo} FOR UPDATE")
    FinanceTransaction lockBySerialNo(@Param("serialNo") String serialNo);
}
