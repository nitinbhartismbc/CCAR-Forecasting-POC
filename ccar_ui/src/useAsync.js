import {useEffect, useState} from "react";

// Runs fn(signal) whenever deps change. While deps and result are out of step it reports loading,
// so a screen never shows data that belongs to the previous selection.
//  - fn receives an AbortSignal; pass it to the api call ({signal}) so a superseded or unmounted request is
//    cancelled on the network, not just ignored.
//  - errors thrown synchronously by fn are reported like rejected promises.
//  - cancellations (AbortError) are never shown as errors.
/*
Remaining known limitations:
JSON.stringify(deps) dependency tracking (deps must be serialisable).
ESLint dependency suppression.
 */
export function useAsync(fn, deps) {
    const [tick, setTick] = useState(0);
    const [s, setS] = useState({key: null, data: null, error: null});
    const key = JSON.stringify([...deps, tick]);
    useEffect(() => {
        const controller = new AbortController();
        let alive = true;
        Promise.resolve()
            .then(() => fn(controller.signal))
            .then(
                (data) => alive && setS({key, data, error: null}),
                (error) => {
                    if (!alive || error?.name === "AbortError") return;
                    setS({key, data: null, error});
                });
        return () => {
            alive = false;
            controller.abort();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key]);
    const ready = s.key === key;
    return {
        loading: !ready,
        data: ready ? s.data : null,
        error: ready ? s.error : null,
        reload: () => setTick((t) => t + 1)
    };
}
