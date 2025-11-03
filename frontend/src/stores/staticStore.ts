import {makeAutoObservable, observable} from "mobx";
import {UserType} from "../utils/UserType";
import api from "../utils/api";

export type TemplateItem = { 
    name: string;
    content: string;
}

export type FieldConfig = {
    minLength: number;
    maxLength: number;
    placeholder: string;
    templates: TemplateItem[];
}

class StaticStore {

    // Settlement Field Config property ---
    _settlementFieldConfig: FieldConfig = {
        minLength: 10,
        maxLength: 500, 
        placeholder: "Enter settlement instructions here or select a template above...",
        templates: [
            {
                name: "Cash Wire Transfer (JPM)", 
                content: "Settle via JPM New York, Account: 123456789, Further Credit: ABC Corp Trading Account"
            },
            {
                name: "DVP Euroclear Securities", 
                content: "DVP settlement through Euroclear, ISIN confirmation required before settlement"
            },
            {
                name: "FedWire Instructions (Cash)", 
                content: "Cash settlement only, wire instructions: Federal Reserve Bank routing 123456789"
            },
            {
                name: "Physical Delivery (Warehouse)", 
                content: "Physical delivery to warehouse facility, contact operations team for coordination"
            },
        ],
    };
    // --- END OF SETTLEMENT FIELD CONFIG PROPERTY ---

    _userTypeCache: UserType[] = [];
    _isLoading: boolean = false;
    _error: string | null = null;
    _currencyValues: string[] = [];
    _legTypeValues: string[] = [];
    _tradeTypeValues: string[] = [];
    _tradeStatusValues: string[] = [];
    _indexValues: string[] = [];
    _holidayCalendarValues: string[] = [];
    _scheduleValues: string[] = [];
    _businessDayConventionValues: string[] = [];
    _payRecValues: string[] = [];
    _counterpartyValues: string[] = [];
    _bookValues: string[] = [];
    _deskValues: string[] = [];
    _subDeskValues: string[] = [];
    _costCenterValues: string[] = [];
    _userValues: string[] = [];
    _tradeSubTypeValues: string[] = [];

    // Getter for the Settlement Field Config ---
     get settlementFieldConfig(): FieldConfig {
        return this._settlementFieldConfig;
    }
    // --- END OF SETTLEMENT FIELD CONFIG GETTER ---

    get error(): string | null {
        return this._error;
    }

    set error(value: string | null) {
        this._error = value;
    }

    get userTypeCache(): UserType[] {
        return this._userTypeCache;
    }

    set userTypeCache(value: UserType[]) {
        this._userTypeCache = value;
    }

    get isLoading(): boolean {
        return this._isLoading;
    }

    set isLoading(value: boolean) {
        this._isLoading = value;
    }

    get currencyValues() {
        return this._currencyValues;
    }

    set currencyValues(v: string[]) {
        this._currencyValues = v;
    }

    get legTypeValues() {
        return this._legTypeValues;
    }

    set legTypeValues(v: string[]) {
        this._legTypeValues = v;
    }

    get tradeTypeValues() {
        return this._tradeTypeValues;
    }

    set tradeTypeValues(v: string[]) {
        this._tradeTypeValues = v;
    }

    get tradeStatusValues() {
        return this._tradeStatusValues;
    }

    set tradeStatusValues(v: string[]) {
        this._tradeStatusValues = v;
    }

    get indexValues() {
        return this._indexValues;
    }

    set indexValues(v: string[]) {
        this._indexValues = v;
    }

    get holidayCalendarValues() {
        return this._holidayCalendarValues;
    }

    set holidayCalendarValues(v: string[]) {
        this._holidayCalendarValues = v;
    }

    get scheduleValues() {
        return this._scheduleValues;
    }

    set scheduleValues(v: string[]) {
        this._scheduleValues = v;
    }

    get businessDayConventionValues() {
        return this._businessDayConventionValues;
    }

    set businessDayConventionValues(v: string[]) {
        this._businessDayConventionValues = v;
    }

    get payRecValues() {
        return this._payRecValues;
    }

    set payRecValues(v: string[]) {
        this._payRecValues = v;
    }

    get counterpartyValues() {
        return this._counterpartyValues;
    }

    set counterpartyValues(v: string[]) {
        this._counterpartyValues = v;
    }

    get bookValues() {
        return this._bookValues;
    }

    set bookValues(v: string[]) {
        this._bookValues = v;
    }

    get deskValues() {
        return this._deskValues;
    }

    set deskValues(v: string[]) {
        this._deskValues = v;
    }

    get subDeskValues() {
        return this._subDeskValues;
    }

    set subDeskValues(v: string[]) {
        this._subDeskValues = v;
    }

    get costCenterValues() {
        return this._costCenterValues;
    }

    set costCenterValues(v: string[]) {
        this._costCenterValues = v;
    }

    get userValues() {
        return this._userValues;
    }

    set userValues(v: string[]) {
        this._userValues = v;
    }

    get tradeSubTypeValues() {
        return this._tradeSubTypeValues;
    }

    set tradeSubTypeValues(v: string[]) {
        this._tradeSubTypeValues = v;
    }

    constructor() {
        makeAutoObservable(this, {
            _userTypeCache: observable,
            _isLoading: observable,
            _error: observable
        });
    }

    async fetchAllStaticValues() {
        this.isLoading = true;
        try {
            const [
                currencies,
                legTypes,
                tradeTypes,
                tradeStatuses,
                indices,
                holidayCalendars,
                schedules,
                businessDayConventions,
                payRecs,
                counterparties,
                books,
                desks,
                subDesks,
                costCenters,
                users,
                tradeSubTypes
            ] = await Promise.all([
                api.get('/currencies/values'),
                api.get('/legTypes/values'),
                api.get('/tradeTypes/values'),
                api.get('/tradeStatus/values'),
                api.get('/indices/values'),
                api.get('/holidayCalendars/values'),
                api.get('/schedules/values'),
                api.get('/businessDayConventions/values'),
                api.get('/payRecs/values'),
                api.get('/counterparties/values'),
                api.get('/books/values'),
                api.get('/desks/values'),
                api.get('/subdesks/values'),
                api.get('/costCenters/values'),
                api.get('/users/values'),
                api.get('/tradeSubTypes/values'),
            ]);
            this.currencyValues = currencies.data;
            this.legTypeValues = legTypes.data;
            this.tradeTypeValues = tradeTypes.data;
            this.tradeStatusValues = tradeStatuses.data;
            this.indexValues = indices.data;
            this.holidayCalendarValues = holidayCalendars.data;
            this.scheduleValues = schedules.data;
            this.businessDayConventionValues = businessDayConventions.data;
            this.payRecValues = payRecs.data;
            this.counterpartyValues = counterparties.data;
            this.bookValues = books.data;
            this.deskValues = desks.data;
            this.subDeskValues = subDesks.data;
            this.costCenterValues = costCenters.data;
            this.userValues = users.data;
            this.tradeSubTypeValues = tradeSubTypes.data;
            this.isLoading = false;
            console.log(this.counterpartyValues)
            return this.counterpartyValues;
        } catch (e) {
            this.error =  'Failed to fetch static values ' + e;
            this.isLoading = false;
        }
    }

    async fetchUserProfiles() {
        this._isLoading = true;
        try {
            const response = await api.get('/userProfiles');
            this._userTypeCache = response.data;
        } catch (error) {
            this._error = 'Failed to fetch user profiles' + error;
        } finally {
            this._isLoading = false;
        }
    }
}


const staticStore = new StaticStore();
export default staticStore;