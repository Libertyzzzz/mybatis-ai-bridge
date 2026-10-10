package com.example.mapper;

import com.example.entity.Order;
import com.example.query.OrderQuery;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

public interface OrderMapper {

    @Select({"<script>",
            "SELECT * FROM t_order",
            "<where>",
            "  <if test='userId != null'>AND user_id = #{userId}</if>",
            "</where>",
            "</script>"})
    List<Order> findByUser(@Param("userId") Long userId);

    @SelectProvider(type = OrderSqlProvider.class, method = "search")
    List<Order> search(OrderQuery query);
}
