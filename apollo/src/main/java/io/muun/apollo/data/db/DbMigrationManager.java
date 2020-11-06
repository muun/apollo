package io.muun.apollo.data.db;

import io.muun.apollo.domain.errors.DbMigrationError;

import android.database.SQLException;
import androidx.sqlite.db.SupportSQLiteDatabase;
import timber.log.Timber;

import java.util.SortedMap;
import java.util.TreeMap;

public class DbMigrationManager {

    private final SortedMap<Integer, String[]> migrations = new TreeMap<>();

    private static final String MIGRATION_2_CREATE_CONTACTS_TABLE = ""
            + "CREATE TABLE contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    first_name TEXT NOT NULL,\n"
            + "    last_name TEXT NOT NULL,\n"
            + "    profile_picture_url TEXT,\n"
            + "    root_hd_public_key TEXT\n"
            + ")";

    private static final String MIGRATION_2_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER UNIQUE,\n"
            + "    sender_hid INTEGER,\n"
            + "    receiver_hid INTEGER,\n"
            + "    amount INTEGER NOT NULL,\n"
            + "    fee INTEGER NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_4_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    sender_hid INTEGER,\n"
            + "    receiver_hid INTEGER,\n"
            + "    external_receiver_address TEXT,\n"
            + "    amount INTEGER NOT NULL,\n"
            + "    fee INTEGER NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_4_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS =
            "INSERT INTO operations SELECT * FROM tmp_operations";

    private static final String MIGRATION_6_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    sender_hid INTEGER,\n"
            + "    receiver_hid INTEGER,\n"
            + "    external_receiver_address TEXT,\n"
            + "    amount INTEGER NOT NULL,\n"
            + "    fee INTEGER NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_6_CREATE_CONTACTS_TABLE = ""
            + "CREATE TABLE contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    first_name TEXT NOT NULL,\n"
            + "    last_name TEXT NOT NULL,\n"
            + "    profile_picture_url TEXT,\n"
            + "    root_hd_public_key TEXT\n"
            + ")";

    private static final String MIGRATION_7_CREATE_PHONE_CONTACTS_TABLE = ""
            + "CREATE TABLE phone_contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    internal_id TEXT NOT NULL,\n"
            + "    name TEXT NOT NULL,\n"
            + "    phone_number TEXT NOT NULL,\n"
            + "    phone_number_hash TEXT NOT NULL\n"
            + ")";

    private static final String MIGRATION_8_CREATE_CONTACTS_TABLE = ""
            + "CREATE TABLE contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    first_name TEXT NOT NULL,\n"
            + "    last_name TEXT NOT NULL,\n"
            + "    profile_picture_url TEXT,\n"
            + "    public_key TEXT NOT NULL,\n"
            + "    public_key_path TEXT NOT NULL\n"
            + ")";

    private static final String MIGRATION_8_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_CONTACTS =
            "INSERT INTO contacts SELECT *, '' FROM tmp_contacts";

    private static final String MIGRATION_10_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    sender_hid INTEGER,\n"
            + "    receiver_hid INTEGER,\n"
            + "    receiver_address TEXT,\n"
            + "    receiver_address_derivation_path TEXT,\n"
            + "    amount INTEGER NOT NULL,\n"
            + "    fee INTEGER NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_10_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS = ""
            + "INSERT INTO operations"
            + "    (\"id\", \"hid\", \"sender_hid\", \"receiver_hid\", \"receiver_address\","
            + "     \"amount\", \"fee\", \"confirmations\", \"hash\", \"description\", \"status\","
            + "     \"creation_date\", \"amount_in_input_currency\","
            + "     \"amount_in_primary_currency\", \"exchange_rate_window_hid\")\n"
            + "SELECT * FROM tmp_operations";

    private static final String MIGRATION_11_CREATE_CONTACTS_TABLE = ""
            + "CREATE TABLE contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE REFERENCES public_profiles(hid),\n"
            + "    public_key TEXT NOT NULL,\n"
            + "    public_key_path TEXT NOT NULL,\n"
            + "    last_derivation_index INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_11_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    is_incoming INTEGER NOT NULL,\n"
            + "    is_external INTEGER NOT NULL,\n"
            + "    sender_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    sender_is_external INTEGER NOT NULL,\n"
            + "    receiver_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    receiver_is_external INTEGER NOT NULL,\n"
            + "    receiver_address TEXT,\n"
            + "    receiver_address_derivation_path TEXT,\n"
            + "    amount_in_satoshis INTEGER NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    fee_in_satoshis INTEGER NOT NULL,\n"
            + "    fee_in_input_currency TEXT NOT NULL,\n"
            + "    fee_in_primary_currency TEXT NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_11_CREATE_PUBLIC_PROFILES_TABLE = ""
            + "CREATE TABLE public_profiles (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    first_name TEXT NOT NULL,\n"
            + "    last_name TEXT NOT NULL,\n"
            + "    profile_picture_url TEXT\n"
            + ")";

    private static final String MIGRATION_12_CREATE_PHONE_CONTACTS_TABLE = ""
            + "CREATE TABLE phone_contacts (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    internal_id TEXT NOT NULL,\n"
            + "    name TEXT NOT NULL,\n"
            + "    phone_number TEXT NOT NULL,\n"
            + "    phone_number_hash TEXT,\n"
            + "    first_seen INTEGER NOT NULL,\n"
            + "    last_seen INTEGER NOT NULL,\n"
            + "    last_updated INTEGER NOT NULL,\n"
            + "    UNIQUE (internal_id, phone_number)\n"
            + ")";

    private static final String MIGRATION_14_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    direction TEXT NOT NULL,\n"
            + "    is_external INTEGER NOT NULL,\n"
            + "    sender_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    sender_is_external INTEGER NOT NULL,\n"
            + "    receiver_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    receiver_is_external INTEGER NOT NULL,\n"
            + "    receiver_address TEXT,\n"
            + "    receiver_address_derivation_path TEXT,\n"
            + "    amount_in_satoshis INTEGER NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    fee_in_satoshis INTEGER NOT NULL,\n"
            + "    fee_in_input_currency TEXT NOT NULL,\n"
            + "    fee_in_primary_currency TEXT NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_14_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS = ""
            + "INSERT INTO operations SELECT * FROM tmp_operations";

    private static final String MIGRATION_15_CREATE_SATELLITE_PAIRINGS_TABLE = ""
            + "CREATE TABLE satellite_pairings (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    satellite_session_uuid TEXT NOT NULL UNIQUE,\n"
            + "    apollo_session_uuid TEXT NOT NULL UNIQUE,\n"
            + "    status TEXT NOT NULL,\n"
            + "    browser TEXT,\n"
            + "    os_version TEXT,\n"
            + "    ip TEXT,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    last_active TEXT\n"
            + ")";

    private static final String MIGRATION_16_CREATE_HARDWARE_WALLETS = ""
            + "CREATE TABLE hardware_wallets (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    model TEXT NOT NULL,\n"
            + "    label TEXT NOT NULL,\n"
            + "    base_public_key TEXT NOT NULL,\n"
            + "    base_public_key_path TEXT NOT NULL,\n"
            + "    created_at TEXT NOT NULL,\n"
            + "    last_paired_at TEXT NOT NULL,\n"
            + "    is_paired INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_20_COPY_BRAND_FROM_HARDWARE_WALLET_MODEL = ""
            + "UPDATE hardware_wallets\n"
            + "SET\n"
            + "    brand = upper(substr(model, 1, instr(model, '/') - 1)),\n"
            + "    model = substr(model, instr(model, '/') + 1)";

    private static final String MIGRATION_21_CREATE_OPERATION_SWAPS_TABLE = ""
            + "CREATE TABLE submarine_swaps (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    houston_uuid TEXT NOT NULL UNIQUE,\n"
            + "    invoice TEXT NOT NULL,\n"
            + "    receiver_alias TEXT,\n"
            + "    receiver_network_addresses TEXT NOT NULL,\n"
            + "    receiver_public_key TEXT NOT NULL,\n"
            + "    funding_output_address TEXT NOT NULL,\n"
            + "    funding_output_amount_in_satoshis INTEGER NOT NULL,\n"
            + "    funding_confirmations_needed INTEGER NOT NULL,\n"
            + "    funding_user_lock_time INTEGER NOT NULL,\n"
            + "    funding_user_refund_address TEXT NOT NULL,\n"
            + "    funding_user_refund_address_path TEXT NOT NULL,\n"
            + "    funding_user_refund_address_version INTEGER NOT NULL,\n"
            + "    funding_server_payment_hash_in_hex TEXT NOT NULL,\n"
            + "    funding_server_public_key_in_hex TEXT NOT NULL,\n"
            + "    sweep_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    lightning_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    expires_at TEXT NOT NULL,\n"
            + "    payed_at TEXT,\n"
            + "    preimage_in_hex TEXT\n"
            + ")";

    private static final String MIGRATION_26_CREATE_SUBMARINE_SWAPS_TABLE = ""
            + "CREATE TABLE submarine_swaps (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    houston_uuid TEXT NOT NULL UNIQUE,\n"
            + "    invoice TEXT NOT NULL,\n"
            + "    receiver_alias TEXT,\n"
            + "    receiver_network_addresses TEXT NOT NULL,\n"
            + "    receiver_public_key TEXT NOT NULL,\n"
            + "    funding_output_address TEXT NOT NULL,\n"
            + "    funding_output_amount_in_satoshis INTEGER NOT NULL,\n"
            + "    funding_confirmations_needed INTEGER NOT NULL,\n"
            + "    funding_user_lock_time INTEGER,\n"       // DROPPING NOT NULL CONSTRAINT HERE
            + "    funding_user_refund_address TEXT NOT NULL,\n"
            + "    funding_user_refund_address_path TEXT NOT NULL,\n"
            + "    funding_user_refund_address_version INTEGER NOT NULL,\n"
            + "    funding_server_payment_hash_in_hex TEXT NOT NULL,\n"
            + "    funding_server_public_key_in_hex TEXT NOT NULL,\n"
            + "    sweep_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    lightning_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    expires_at TEXT NOT NULL,\n"
            + "    payed_at TEXT,\n"
            + "    preimage_in_hex TEXT,\n"
            + "    will_pre_open_channel INTEGER NOT NULL,\n"
            + "    channel_open_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    channel_close_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    funding_script_version INTEGER NOT NULL,\n"
            + "    funding_expiration_in_blocks INTEGER,\n"
            + "    funding_user_public_key TEXT,\n"
            + "    funding_user_public_key_path TEXT,\n"
            + "    funding_muun_public_key TEXT,\n"
            + "    funding_muun_public_key_path TEXT\n"
            + ")";

    private static final String MIGRATION_26_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_SUBMARINE_SWAPS =
            "INSERT INTO submarine_swaps SELECT * FROM tmp_submarine_swaps";

    private static final String MIGRATION_29_CREATE_OPERATIONS_TABLE = ""
            + "CREATE TABLE operations (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    hid INTEGER NOT NULL UNIQUE,\n"
            + "    direction TEXT NOT NULL,\n"
            + "    is_external INTEGER NOT NULL,\n"
            + "    sender_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    sender_is_external INTEGER NOT NULL,\n"
            + "    receiver_hid INTEGER REFERENCES public_profiles(hid),\n"
            + "    receiver_is_external INTEGER NOT NULL,\n"
            + "    receiver_address TEXT,\n"
            + "    receiver_address_derivation_path TEXT,\n"
            + "    amount_in_satoshis INTEGER NOT NULL,\n"
            + "    amount_in_input_currency TEXT NOT NULL,\n"
            + "    amount_in_primary_currency TEXT NOT NULL,\n"
            + "    fee_in_satoshis INTEGER NOT NULL,\n"
            + "    fee_in_input_currency TEXT NOT NULL,\n"
            + "    fee_in_primary_currency TEXT NOT NULL,\n"
            + "    confirmations INTEGER NOT NULL,\n"
            + "    hash TEXT,\n"
            + "    description TEXT,\n"
            + "    status TEXT NOT NULL,\n"
            + "    creation_date TEXT NOT NULL,\n"
            + "    exchange_rate_window_hid INTEGER NOT NULL,\n"
            + "    submarine_swap_houston_uuid TEXT REFERENCES submarine_swaps(houston_uuid)\n"
            + ")";

    private static final String MIGRATION_29_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS =
            "INSERT INTO operations SELECT"
                    + " id,"
                    + " hid,"
                    + " direction,"
                    + " is_external,"
                    + " sender_hid,"
                    + " sender_is_external,"
                    + " receiver_hid,"
                    + " receiver_is_external,"
                    + " receiver_address, receiver_address_derivation_path,"
                    + " amount_in_satoshis,"
                    + " amount_in_input_currency,"
                    + " amount_in_primary_currency,"
                    + " fee_in_satoshis,"
                    + " fee_in_input_currency,"
                    + " fee_in_primary_currency,"
                    + " confirmations,"
                    + " hash,"
                    + " description,"
                    + " status,"
                    + " creation_date,"
                    + " exchange_rate_window_hid,"
                    + " submarine_swap_houston_uuid"
                    + " FROM tmp_operations";

    private static final String MIGRATION_32_CREATE_SUBMARINE_SWAPS_TABLE = ""
            + "CREATE TABLE submarine_swaps (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    houston_uuid TEXT NOT NULL UNIQUE,\n"
            + "    invoice TEXT NOT NULL,\n"
            + "    receiver_alias TEXT,\n"
            + "    receiver_network_addresses TEXT NOT NULL,\n"
            + "    receiver_public_key TEXT NOT NULL,\n"
            + "    funding_output_address TEXT NOT NULL,\n"
            + "    funding_output_amount_in_satoshis INTEGER NOT NULL,\n"
            + "    funding_confirmations_needed INTEGER NOT NULL,\n"
            + "    funding_user_lock_time INTEGER,\n"
            + "    funding_user_refund_address TEXT NOT NULL,\n"
            + "    funding_user_refund_address_path TEXT NOT NULL,\n"
            + "    funding_user_refund_address_version INTEGER NOT NULL,\n"
            + "    funding_server_payment_hash_in_hex TEXT NOT NULL,\n"
            + "    funding_server_public_key_in_hex TEXT NOT NULL,\n"
            + "    sweep_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    lightning_fee_in_satoshis INTEGER NOT NULL,\n"
            + "    expires_at TEXT NOT NULL,\n"
            + "    payed_at TEXT,\n"
            + "    preimage_in_hex TEXT,\n"
            + "    funding_script_version INTEGER NOT NULL,\n"
            + "    funding_expiration_in_blocks INTEGER,\n"
            + "    funding_user_public_key TEXT,\n"
            + "    funding_user_public_key_path TEXT,\n"
            + "    funding_muun_public_key TEXT,\n"
            + "    funding_muun_public_key_path TEXT,\n"
            + "    funding_output_debt_type TEXT NOT NULL,\n"
            + "    funding_output_debt_amount_in_satoshis INTEGER NOT NULL\n"
            + ")";

    private static final String MIGRATION_32_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_SUBMARINE_SWAPS =
            "INSERT INTO submarine_swaps SELECT"
                    + "    id,\n"
                    + "    houston_uuid,\n"
                    + "    invoice,\n"
                    + "    receiver_alias,\n"
                    + "    receiver_network_addresses,\n"
                    + "    receiver_public_key,\n"
                    + "    funding_output_address,\n"
                    + "    funding_output_amount_in_satoshis,\n"
                    + "    funding_confirmations_needed,\n"
                    + "    funding_user_lock_time INTEGER,\n"
                    + "    funding_user_refund_address,\n"
                    + "    funding_user_refund_address_path,\n"
                    + "    funding_user_refund_address_version,\n"
                    + "    funding_server_payment_hash_in_hex,\n"
                    + "    funding_server_public_key_in_hex,\n"
                    + "    sweep_fee_in_satoshis,\n"
                    + "    lightning_fee_in_satoshis,\n"
                    + "    expires_at,\n"
                    + "    payed_at,\n"
                    + "    preimage_in_hex,\n"
                    + "    funding_script_version ,\n"
                    + "    funding_expiration_in_blocks,\n"
                    + "    funding_user_public_key,\n"
                    + "    funding_user_public_key_path,\n"
                    + "    funding_muun_public_key,\n"
                    + "    funding_muun_public_key_path,\n"
                    + "    funding_output_debt_type,\n"
                    + "    funding_output_debt_amount_in_satoshis\n"
                    + "FROM tmp_submarine_swaps";

    private static final String MIGRATION_33_CREATE_INCOMING_SWAP_HTLCS_TABLE = ""
            + "CREATE TABLE incoming_swap_htlcs (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    houston_uuid TEXT NOT NULL UNIQUE,\n"
            + "    expiration_height INTEGER NOT NULL,\n"
            + "    payment_amount_in_satoshis INTEGER NOT NULL,\n"
            + "    fulfillment_fee_subsidy_in_satoshis INTEGER NOT NULL,\n"
            + "    lent_in_satoshis INTEGER NOT NULL,\n"
            + "    swap_server_public_key_in_hex TEXT NOT NULL,\n"
            + "    fulfillment_tx_in_hex TEXT,\n"
            + "    address TEXT NOT NULL,\n"
            + "    output_amount_in_satoshis INTEGER NOT NULL,\n"
            + "    htlc_tx_in_hex TEXT NOT NULL\n"
            + ")";

    private static final String MIGRATION_33_CREATE_INCOMING_SWAPS_TABLE = ""
            + "CREATE TABLE incoming_swaps (\n"
            + "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
            + "    houston_uuid TEXT NOT NULL UNIQUE,\n"
            + "    payment_hash_in_hex TEXT NOT NULL,\n"
            + "    sphinx_packet_in_hex TEXT,\n"
            + "    incoming_swap_htlc_houston_uuid TEXT \n"
            + "    REFERENCES incoming_swap_htlcs(houston_uuid)\n"
            + ")";

    /**
     * Constructor. Here go all the database migrations.
     *
     * <p>If you need a create table statement you should copy the raw string to a variable here,
     * because the generated statement will change with time and new migrations.
     */
    DbMigrationManager() {

        add(2,
                MigrationsModel.MIGRATION_2_DROP_CONTACTS_TABLE,
                MIGRATION_2_CREATE_CONTACTS_TABLE,
                MigrationsModel.MIGRATION_2_DROP_OPERATIONS_TABLE,
                MIGRATION_2_CREATE_OPERATIONS_TABLE
        );

        add(3,
                MigrationsModel.MIGRATION_3_ADD_EXTERNAL_ADDRESS_COLUMN_TO_OPERATIONS_TABLE
        );

        // Sqlite3 doesn't support ALTER COLUMN, so we have to do this instead:
        //
        //  1. Rename your table to a temporary name.
        //  2. Create a table exactly as the original one, except for the column in question.
        //  3. Insert all the rows from the temporary table to the new one.
        //  4. Delete the temporary table.
        add(4,
                MigrationsModel.MIGRATION_4_MOVE_OPERATIONS_TO_A_TEMPORARY_TABLE,
                MIGRATION_4_CREATE_OPERATIONS_TABLE,
                MIGRATION_4_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS,
                MigrationsModel.MIGRATION_4_DROP_TEMPORARY_TABLE
        );

        add(5,
                MigrationsModel.MIGRATION_5_DROP_TASKS_TABLE
        );

        add(6,
                MigrationsModel.MIGRATION_6_DROP_OPERATIONS_TABLE,
                MigrationsModel.MIGRATION_6_DROP_CONTACTS_TABLE,
                MIGRATION_6_CREATE_OPERATIONS_TABLE,
                MIGRATION_6_CREATE_CONTACTS_TABLE
        );

        add(7,
                MIGRATION_7_CREATE_PHONE_CONTACTS_TABLE
        );

        add(8,
                MigrationsModel.MIGRATION_8_MOVE_CONTACTS_TO_A_TEMPORARY_TABLE,
                MIGRATION_8_CREATE_CONTACTS_TABLE,
                MIGRATION_8_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_CONTACTS,
                MigrationsModel.MIGRATION_8_DROP_TEMPORARY_TABLE
        );

        add(9,
                MigrationsModel.MIGRATION_9_ADD_LAST_DERIVATION_INDEX_COLUMN_TO_CONTACTS_TABLE
        );

        add(10,
                MigrationsModel.MIGRATION_10_MOVE_OPERATIONS_TO_A_TEMPORARY_TABLE,
                MIGRATION_10_CREATE_OPERATIONS_TABLE,
                MIGRATION_10_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS,
                MigrationsModel.MIGRATION_10_DROP_TEMPORARY_TABLE
        );

        // We are on mainnet now ;) drop everything
        add(11,
                MigrationsModel.MIGRATION_11_DROP_CONTACTS_TABLE,
                MigrationsModel.MIGRATION_11_DROP_OPERATIONS_TABLE,
                MIGRATION_11_CREATE_CONTACTS_TABLE,
                MIGRATION_11_CREATE_OPERATIONS_TABLE,
                MIGRATION_11_CREATE_PUBLIC_PROFILES_TABLE
        );

        add(12,
                MigrationsModel.MIGRATION_12_DROP_PHONE_CONTACTS,
                MIGRATION_12_CREATE_PHONE_CONTACTS_TABLE
        );

        add(13,
                MigrationsModel.MIGRATION_13_ADD_CONTACT_ADDRESS_VERSION,
                MigrationsModel.MIGRATION_13_ADD_CONTACT_COSIGNING_PUBKEY,
                MigrationsModel.MIGRATION_13_ADD_CONTACT_COSIGNING_PUBKEY_PATH
        );

        // We forgot to add a migration changing the is_incoming column to direction, so we change
        // it now. The thing is, some users might have already created the table with the direction
        // column (if they cleared data, or uninstalled/installed the app).
        add(14,
                MigrationsModel.MIGRATION_14_MOVE_OPERATIONS_TO_A_TEMPORARY_TABLE,
                MIGRATION_14_CREATE_OPERATIONS_TABLE,
                // The following copy will fail for users that don't have the new column yet (ie.
                // users that actually need the migration), but that's ok, since their operations
                // table is empty: we've just logged the user out.
                MIGRATION_14_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS,
                MigrationsModel.MIGRATION_14_DROP_TEMPORARY_TABLE
        );

        add(15, MIGRATION_15_CREATE_SATELLITE_PAIRINGS_TABLE);

        add(16, MIGRATION_16_CREATE_HARDWARE_WALLETS);

        add(17, MigrationsModel.MIGRATION_17_ADD_HARDWARE_WALLET_TO_OP);

        add(18, MigrationsModel.MIGRATION_18_ADD_ENCRYPTION_KEY_TO_PAIRINGS);

        add(19,
                MigrationsModel.MIGRATION_19_ADD_BRAND_TO_HARDWARE_WALLET,
                MIGRATION_20_COPY_BRAND_FROM_HARDWARE_WALLET_MODEL
        );

        add(20, MigrationsModel.MIGRATION_21_ADD_IS_IN_USE_TO_PAIRINGS);

        add(21, MIGRATION_21_CREATE_OPERATION_SWAPS_TABLE);

        add(22, MigrationsModel.MIGRATION_22_ADD_SUBMARINE_SWAP_TO_OPERATION);

        add(23, MigrationsModel.MIGRATION_23_ADD_SUBMARINE_SWAP_WILL_PRE_OPEN_CHANNEL);

        add(24, MigrationsModel.MIGRATION_24_ADD_SUBMARINE_SWAP_CHANNEL_OPEN_FEE);
        add(25, MigrationsModel.MIGRATION_25_ADD_SUBMARINE_SWAP_CHANNEL_CLOSE_FEE);

        // Sqlite3 doesn't support ALTER COLUMN, so we have to do this instead:
        //
        //  1. Rename your table to a temporary name.
        //  2. Create a table exactly as the original one, except for the column in question.
        //  3. Insert all the rows from the temporary table to the new one.
        //  4. Delete the temporary table.

        add(26, MigrationsModel.MIGRATION_26_MOVE_SUBMARINE_SWAPS_TO_A_TEMPORARY_TABLE,
                MIGRATION_26_CREATE_SUBMARINE_SWAPS_TABLE,
                MIGRATION_26_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_SUBMARINE_SWAPS,
                MigrationsModel.MIGRATION_26_DROP_TEMPORARY_TABLE
        );

        add(27, MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_SCRIPT_VERSION,
                MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_EXPIRATION_IN_BLOCKS,
                MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_USER_PUBLIC_KEY,
                MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_USER_PUBLIC_KEY_PATH,
                MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_MUUN_PUBLIC_KEY,
                MigrationsModel.MIGRATION_27_ADD_SWAP_FUNDING_OUTPUT_MUUN_PUBLIC_KEY_PATH
        );

        add(28, MigrationsModel.MIGRATION_28_ADD_SWAP_DEBT_TYPE,
                MigrationsModel.MIGRATION_28_ADD_SWAP_DEBT_AMOUNT);

        // We want to delete hws foreign key in operations table but we can't drop column...
        // Sqlite3 doesn't support ALTER COLUMN, so we have to do this instead:
        //
        //  1. Rename your table to a temporary name.
        //  2. Create a table exactly as the original one, except for the column in question.
        //  3. Insert all the rows from the temporary table to the new one.
        //  4. Delete the temporary table.

        add(29,
                MigrationsModel.MIGRATION_29_MOVE_OPERATIONS_TO_A_TEMPORARY_TABLE,
                MIGRATION_29_CREATE_OPERATIONS_TABLE,
                MIGRATION_29_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_OPERATIONS,
                MigrationsModel.MIGRATION_29_DROP_TEMPORARY_TABLE
        );

        add(30, MigrationsModel.MIGRATION_30_DROP_HARDWARE_WALLETS_TABLE);
        add(31, MigrationsModel.MIGRATION_31_DROP_SATELLITE_PAIRINGS_TABLE);

        // We want to drop topup columns in swaps table but we can't drop column...
        // Sqlite3 doesn't support ALTER COLUMN, so we have to do this instead:
        //
        //  1. Rename your table to a temporary name.
        //  2. Create a table exactly as the original one, except for the column in question.
        //  3. Insert all the rows from the temporary table to the new one.
        //  4. Delete the temporary table.

        add(32,
                MigrationsModel.MIGRATION_32_MOVE_SUBMARINE_SWAPS_TO_A_TEMPORARY_TABLE,
                MIGRATION_32_CREATE_SUBMARINE_SWAPS_TABLE,
                MIGRATION_32_COPY_ROWS_FROM_TEMPORARY_TABLE_INTO_SUBMARINE_SWAPS,
                MigrationsModel.MIGRATION_32_DROP_TEMPORARY_TABLE
        );

        add(33,
                MIGRATION_33_CREATE_INCOMING_SWAP_HTLCS_TABLE,
                MIGRATION_33_CREATE_INCOMING_SWAPS_TABLE,
                MigrationsModel.MIGRATION_33_OPERATIONS_REFERENCE_INCOMING_SWAPS
        );
    }

    /**
     * Add a new migration.
     */
    private void add(int version, String... statements) {

        if (migrations.containsKey(version)) {
            throw new IllegalArgumentException(
                    "Migration for version " + version + " already exists");
        }

        migrations.put(version, statements);
    }

    /**
     * This method will be called from the DaoManager to execute the migrations. Do not call this
     * method directly.
     */
    public void run(SupportSQLiteDatabase database, int oldVersion, int newVersion) {

        for (int version = oldVersion + 1; version <= newVersion; version++) {

            final String[] statements = migrations.get(version);

            if (statements == null) {
                continue;
            }

            Timber.d("Running database migration %d...", version);

            database.beginTransaction();

            try {
                for (String statement : statements) {
                    try {
                        database.execSQL(statement);
                    } catch (SQLException e) {
                        Timber.e(new DbMigrationError(version, statement, e));
                    }
                }

                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    }
}
