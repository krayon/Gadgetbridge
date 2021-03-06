package nodomain.freeyourgadget.gadgetbridge.devices;

import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import de.greenrobot.dao.query.WhereCondition;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

/**
 * Base class for all sample providers. A Sample provider is device specific and provides
 * access to the device specific samples. There are both read and write operations.
 * @param <T> the sample type
 */
public abstract class AbstractSampleProvider<T extends AbstractActivitySample> implements SampleProvider<T> {
    private static final WhereCondition[] NO_CONDITIONS = new WhereCondition[0];
    private final DaoSession mSession;

    protected AbstractSampleProvider(DaoSession session) {
        mSession = session;
    }

    public DaoSession getSession() {
        return mSession;
    }

    @Override
    public List<T> getAllActivitySamples(int timestamp_from, int timestamp_to) {
        return getGBActivitySamples(timestamp_from, timestamp_to, ActivityKind.TYPE_ALL);
    }

    @Override
    public List<T> getActivitySamples(int timestamp_from, int timestamp_to) {
        return getGBActivitySamples(timestamp_from, timestamp_to, ActivityKind.TYPE_ACTIVITY);
    }

    @Override
    public List<T> getSleepSamples(int timestamp_from, int timestamp_to) {
        return getGBActivitySamples(timestamp_from, timestamp_to, ActivityKind.TYPE_SLEEP);
    }

    @Override
    public int fetchLatestTimestamp() {
        QueryBuilder<T> qb = getSampleDao().queryBuilder();
        qb.orderDesc(getTimestampSampleProperty());
        qb.limit(1);
        List<T> list = qb.build().list();
        if (list.size() >= 1) {
            return list.get(0).getTimestamp();
        }
        return -1;
    }

    @Override
    public void addGBActivitySample(T activitySample) {
        getSampleDao().insertOrReplace(activitySample);
    }

    @Override
    public void addGBActivitySamples(T[] activitySamples) {
        getSampleDao().insertOrReplaceInTx(activitySamples);
    }

    public void changeStoredSamplesType(int timestampFrom, int timestampTo, int kind) {
        List<T> samples = getAllActivitySamples(timestampFrom, timestampTo);
        for (T sample : samples) {
            sample.setRawKind(kind);
        }
        getSampleDao().updateInTx(samples);
    }

    public void changeStoredSamplesType(int timestampFrom, int timestampTo, int fromKind, int toKind) {
        List<T> samples = getGBActivitySamples(timestampFrom, timestampTo, fromKind);
        for (T sample : samples) {
            sample.setRawKind(toKind);
        }
        getSampleDao().updateInTx(samples);
    }

    protected List<T> getGBActivitySamples(int timestamp_from, int timestamp_to, int activityType) {
        QueryBuilder<T> qb = getSampleDao().queryBuilder();
        Property timestampProperty = getTimestampSampleProperty();
        qb.where(timestampProperty.ge(timestamp_from))
            .where(timestampProperty.le(timestamp_to), getClauseForActivityType(qb, activityType));
        List<T> samples = qb.build().list();
        for (T sample : samples) {
            sample.setProvider(this);
        }
        return samples;
    }

    private WhereCondition[] getClauseForActivityType(QueryBuilder qb, int activityTypes) {
        if (activityTypes == ActivityKind.TYPE_ALL) {
            return NO_CONDITIONS;
        }

        int[] dbActivityTypes = ActivityKind.mapToDBActivityTypes(activityTypes, this);
        WhereCondition activityTypeCondition = getActivityTypeConditions(qb, dbActivityTypes);
        return new WhereCondition[] { activityTypeCondition };
    }

    private WhereCondition getActivityTypeConditions(QueryBuilder qb, int[] dbActivityTypes) {
        // What a crappy QueryBuilder API ;-( QueryBuilder.or(WhereCondition[]) with a runtime array length
        // check would have worked just fine.
        if (dbActivityTypes.length == 0) {
            return null;
        }
        Property rawKindProperty = getRawKindSampleProperty();
        if (dbActivityTypes.length == 1) {
            return rawKindProperty.eq(dbActivityTypes[0]);
        }
        if (dbActivityTypes.length == 2) {
            return qb.or(rawKindProperty.eq(dbActivityTypes[0]),
                    rawKindProperty.eq(dbActivityTypes[1]));
        }
        final int offset = 2;
        int len = dbActivityTypes.length - offset;
        WhereCondition[] trailingConditions = new WhereCondition[len];
        for (int i = 0; i < len; i++) {
            trailingConditions[i] = rawKindProperty.eq(dbActivityTypes[i + offset]);
        }
        return qb.or(rawKindProperty.eq(dbActivityTypes[0]),
                rawKindProperty.eq(dbActivityTypes[1]),
                trailingConditions);
    }

    public abstract AbstractDao<T,?> getSampleDao();

    protected abstract Property getRawKindSampleProperty();
    protected abstract Property getTimestampSampleProperty();
}
