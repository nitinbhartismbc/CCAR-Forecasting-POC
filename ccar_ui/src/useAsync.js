import {useEffect, useState} from "react";

// Runs fn() whenever deps change. While deps and result are out of step it reports loading,
// so a screen never shows data that belongs to the previous selection.
/*
Potential Issues with the file:
JSON.stringify(deps) dependency tracking.
ESLint dependency suppression.
No request cancellation.
Doesn't handle synchronous exceptions.
Potential unnecessary serialization cost.
 */
export function useAsync(fn, deps) {
    const [tick, setTick] = useState(0);
    const [s, setS] = useState({key: null, data: null, error: null});
    const key = JSON.stringify([...deps, tick]);
    useEffect(() => {
        let alive = true;
        fn().then((data) => alive && setS({key, data, error: null}), (error) => alive && setS({
            key,
            data: null,
            error
        }));
        return () => {
            alive = false;
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
