import React from "react";
import Input from "../components/Input";
import Button from "../components/Button";
import api from "../utils/api";
import { SingleTradeModal} from "./SingleTradeModal";
import { getDefaultTrade } from "../utils/tradeUtils";
import userStore from "../stores/userStore";
import LoadingSpinner from "../components/LoadingSpinner";
import Snackbar from "../components/Snackbar";
import {observer} from "mobx-react-lite";
import {useQuery} from '@tanstack/react-query';
import staticStore from "../stores/staticStore";
import {Trade, TradeLeg} from "../utils/tradeTypes";

export const TradeActionsModal: React.FC = observer(() => {
    const [tradeId, setTradeId] = React.useState<string>("");
    const [snackBarOpen, setSnackbarOpen] = React.useState<boolean>(false);
    const [trade, setTrade] = React.useState<Trade | null>(null);
    const [loading, setLoading] = React.useState<boolean>(false);
    const [snackbarMessage, setSnackbarMessage] = React.useState<string>("");
    const [isLoadError, setIsLoadError] = React.useState<boolean>(false);
    const [modalKey, setModalKey] = React.useState(0);

    const {isSuccess, error} = useQuery({
        queryKey: ["staticValues"],
        queryFn: () => staticStore.fetchAllStaticValues(),
        refetchInterval: 30000,
        refetchOnWindowFocus: false,
    });

    React.useEffect(() => {
        if (isSuccess) {
            staticStore.isLoading = false;
            console.log("Static values loaded successfully");
        }
        if (error) {
            staticStore.error = (error).message || 'Unknown error';
        }
    }, [isSuccess, error]);

    // Helper function to format dates from API response
    const processTradeResponse = (tradeData: any) => {
        const convertToDate = (val: string | undefined) => val ? new Date(val) : undefined;
        const dateFields = [
            'tradeDate',
            'startDate',
            'maturityDate',
            'executionDate',
            'lastTouchTimestamp',
            'validityStartDate'
        ];

        const formatDateForInput = (date: Date | undefined) =>
            date ? date.toISOString().slice(0, 10) : undefined;
        
        // Format date fields
        dateFields.forEach(field => {
            if (tradeData[field]) {
                const dateObj = convertToDate(tradeData[field]);
                tradeData[field] = formatDateForInput(dateObj);
            }
        });

        // Ensure tradeLegs is an array of TradeLeg objects
        if (Array.isArray(tradeData.tradeLegs)) {
            tradeData.tradeLegs = tradeData.tradeLegs.map((leg: TradeLeg) => {
                return {
                    ...leg,
                    legId: leg.legId || '',
                    legType: leg.legType || '',
                    rate: leg.rate !== undefined ? leg.rate : '',
                    index: leg.index || '',
                };
            });
        } else {
            tradeData.tradeLegs = [];
        }
        return tradeData as Trade;
    }

    // Original handler (now explicitly for Trade ID lookup)
    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        console.log("Searching for trade ID:", tradeId);
        setLoading(true)
        try {
            const tradeResponse = await api.get(`/trades/${tradeId}`);
            if (tradeResponse.status === 200) {
                const tradeData = processTradeResponse(tradeResponse.data);
                setTrade(tradeData);
                setSnackbarOpen(true)
                setSnackbarMessage("Successfully fetched trade details by ID.");
            } else {
                console.error("Error fetching trade:", tradeResponse.statusText);
                setSnackbarMessage("Error fetching trade details: " + tradeResponse.statusText);
                setIsLoadError(true)
            }
        } catch (error) {
            console.error("Error fetching trade:", error);
            setIsLoadError(true);
            setSnackbarOpen(true);
            setSnackbarMessage("Error fetching trade details: " + (error instanceof Error ? error.message : "Unknown error"));
        } finally {
            setTimeout(() => {
                setSnackbarOpen(false);
                setSnackbarMessage("")
                setIsLoadError(false)
            }, 3000);
            setLoading(false)
            setTradeId("")
        }
    };

    // NEW handler for Settlement Instruction Search
    const handleSearchByInstructions = async (e: React.FormEvent) => {
        e.preventDefault();
        console.log("Searching for trades by SI content:", tradeId);
        
        if (!tradeId) {
            setSnackbarOpen(true);
            setSnackbarMessage("Please enter a term to search for in Settlement Instructions.");
            setIsLoadError(true);
            setTimeout(() => { setSnackbarOpen(false); setIsLoadError(false); }, 3000);
            return;
        }

        setLoading(true);
        // Use the SI search endpoint with the current input value as the search term
        const endpoint = `/trades/search/settlement-instructions?instructions=${encodeURIComponent(tradeId)}`;
        console.log("Using endpoint:", endpoint);

        try {
            const tradeResponse = await api.get(endpoint);
            
            if (tradeResponse.status === 200 && tradeResponse.data.length > 0) {
                // SI search returns a list, but the modal is designed for one trade. 
                // Display the first result found.
                const firstTrade = tradeResponse.data[0];
                const tradeData = processTradeResponse(firstTrade);
                
                setTrade(tradeData);
                setSnackbarOpen(true);
                
                let message = `Found ${tradeResponse.data.length} trade(s) matching SI content. Displaying first result.`;
                if (tradeResponse.data.length === 1) {
                    message = "Successfully fetched trade details by SI content.";
                }
                setSnackbarMessage(message);

            } else if (tradeResponse.status === 200 && tradeResponse.data.length === 0) {
                 setTrade(null);
                 setSnackbarOpen(true);
                 setSnackbarMessage(`No trades found matching SI content: "${tradeId}"`);
                 setIsLoadError(true);
            } else {
                console.error("Error fetching trades by SI:", tradeResponse.statusText);
                setSnackbarMessage("Error fetching trades by SI: " + tradeResponse.statusText);
                setIsLoadError(true);
            }
        } catch (error) {
            console.error("Error fetching trades by SI:", error);
            setIsLoadError(true);
            setSnackbarOpen(true);
            setSnackbarMessage("Error fetching trades by SI: " + (error instanceof Error ? error.message : "Unknown error"));
        } finally {
            setTimeout(() => {
                setSnackbarOpen(false);
                setSnackbarMessage("");
                setIsLoadError(false);
            }, 3000);
            setLoading(false);
            setTradeId("");
        }
    };


    const handleClearAll = () => {
        setTrade(null);
        setTradeId("");
        setSnackbarOpen(false);
        setSnackbarMessage("");
        setIsLoadError(false);
        setLoading(false);
    };
    const handleBookNew = () => {
        const defaultTrade = getDefaultTrade();
        console.log('DEBUG getDefaultTrade:', defaultTrade);
        setTrade(defaultTrade);
        setModalKey(prev => prev + 1);
    };
    const mode = userStore.authorization === "TRADER_SALES" || userStore.authorization === "MO" ? "edit" : "view";
    return (
        <div className={"flex flex-col rounded-lg drop-shadow-2xl mt-0 bg-indigo-50 w-full h-full"}>
            <div className={"flex flex-row items-center justify-center p-4 h-fit w-fit gap-x-2 mb-2 mx-auto"}>
                <Input size={"sm"}
                       type={"search"}
                       required
                       placeholder={"Search by Trade ID or SI"}
                       key={"trade-id"}
                       value={tradeId}
                       onChange={(e) => setTradeId(e.currentTarget.value)}
                       className={"bg-white h-fit w-fit"}/>
                {/* Search by ID (Original Functionality) */}       
                <Button variant={"primary"} type={"button"} size={"sm"} onClick={handleSearch}
                        className={"w-fit h-fit"}>Search ID</Button>
                {/* NEW: Search by Settlement Instructions */}
                <Button variant={"primary"} type={"button"} size={"sm"} onClick={handleSearchByInstructions}
                        className={"w-fit h-fit !bg-green-600 hover:!bg-green-700"}>Search SI</Button>
                <Button variant={"primary"} type={"button"} size={"sm"} onClick={handleClearAll}
                        className={"w-fit h-fit !bg-gray-500 hover:!bg-gray-700"}>Clear</Button>
                { userStore.authorization === "TRADER_SALES" &&
                <Button variant={"primary"} type={"button"} size={"sm"} onClick={handleBookNew}
                        className={"w-fit h-fit"}>Book New</Button>
                }
            </div>
            <div>
                {loading ? <LoadingSpinner/> : null}
                {trade && !loading && <SingleTradeModal key={modalKey} mode={mode} trade={trade} isOpen={!!trade} onClear={handleClearAll}/>}
            </div>
            <Snackbar open={snackBarOpen} message={snackbarMessage} onClose={() => setSnackbarOpen(false)}
                      type={isLoadError ? "error" : "success"}/>
        </div>
    )
})