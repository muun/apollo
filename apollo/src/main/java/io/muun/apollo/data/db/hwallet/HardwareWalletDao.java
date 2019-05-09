package io.muun.apollo.data.db.hwallet;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.HardwareWallet;

import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HardwareWalletDao extends HoustonIdDao<HardwareWallet> {

    /**
     * Constructor.
     */
    @Inject
    public HardwareWalletDao() {

        super(
                HardwareWalletEntity.CREATE_TABLE,
                HardwareWalletEntity::fromModel,
                HardwareWalletEntity::toModel,
                HardwareWalletEntity.TABLE_NAME
        );
    }

    /**
     * Fetches a single HardwareWallet by its Houston id.
     */
    public Observable<HardwareWallet> fetchByHid(long hid) {

        return fetchOneOrFail(HardwareWalletEntity.FACTORY.selectByHid(hid));
    }

    /**
     * Fetches all paired HardwareWallets.
     */
    public Observable<List<HardwareWallet>> fetchPaired() {

        return fetchList(HardwareWalletEntity.FACTORY.selectPaired());
    }

    /**
     * Fetches all HardwareWallets.
     */
    public Observable<List<HardwareWallet>> fetchAllIncludingUnpaired() {

        return fetchList(HardwareWalletEntity.FACTORY.selectAll());
    }
}
