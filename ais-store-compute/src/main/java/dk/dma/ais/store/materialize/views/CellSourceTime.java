package dk.dma.ais.store.materialize.views;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Map.Entry;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Update;

import dk.dma.ais.binary.SixbitException;
import dk.dma.ais.message.AisMessageException;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.materialize.AisMatSchema;
import dk.dma.ais.store.materialize.HashViewBuilder;
import dk.dma.ais.store.materialize.util.TypeSafeMapOfMaps;
import dk.dma.ais.store.materialize.util.TypeSafeMapOfMaps.Key2;
import dk.dma.ais.store.materialize.util.TypeSafeMapOfMaps.Key3;
import dk.dma.enav.model.geometry.Position;

public class CellSourceTime implements HashViewBuilder {
	TypeSafeMapOfMaps<Key3<Integer, String, String>, Long> data = new TypeSafeMapOfMaps<>();
    private SimpleDateFormat timeFormatter;

	@Override
	public void accept(AisPacket aisPacket) {
        Objects.requireNonNull(aisPacket);
		Long timestamp = aisPacket.getBestTimestamp();
		String sourceid = Objects.requireNonNull(aisPacket.getTags().getSourceId());
		Position p;
		try {
			p = Objects.requireNonNull(aisPacket.getAisMessage().getValidPosition());
			Integer cellid = p.getCellInt(1.0);
			
			if (timestamp > 0) {
			    String time = Objects.requireNonNull(timeFormatter.format(new Date(timestamp)));
			    try {
			        Long value = data.get(TypeSafeMapOfMaps.key(cellid, sourceid, time));
			        data.put(TypeSafeMapOfMaps.key(cellid, sourceid, time),value+1);
			    } catch (Exception e) {
			        data.put(TypeSafeMapOfMaps.key(cellid, sourceid, time),0L);
			    }
			    
			}
		} catch (AisMessageException | SixbitException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

    @Override
    public List<RegularStatement> prepare() {
        LinkedList<RegularStatement> list = new LinkedList<>();        
        for (Entry<Key3<Integer, String, String>, Long> e : data) {
            Update upd = QueryBuilder.update(AisMatSchema.TABLE_CELL1_TIME_COUNT);
            upd.setConsistencyLevel(ConsistencyLevel.ONE);
            upd.where(QueryBuilder.eq(AisMatSchema.CELL1_KEY, e.getKey().getK1()));
            upd.where(QueryBuilder.eq(AisMatSchema.SOURCE_KEY, e.getKey().getK2()));
            upd.where(QueryBuilder.eq(AisMatSchema.TIME_KEY, e.getKey().getK3()));
            upd.with(QueryBuilder.set(AisMatSchema.RESULT_KEY, e.getValue()));              
            list.add(upd);
        
        }
        return list;
    }
}