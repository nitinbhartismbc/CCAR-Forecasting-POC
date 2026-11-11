import React from "react";

// Last line of defence: a rendering error anywhere below shows a recoverable message instead of a blank page.
export default class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {error: null};
    }

    static getDerivedStateFromError(error) {
        return {error};
    }

    componentDidCatch(error, info) {
        console.error("Unhandled UI error", error, info?.componentStack);
    }

    render() {
        if (!this.state.error) return this.props.children;
        return (
            <div className="state-box error" role="alert" style={{margin: 24}}>
                <span>Something went wrong in the app. Reloading usually fixes it.
                    <small>{String(this.state.error?.message || "")}</small></span>
                <button className="button small" onClick={() => window.location.reload()}>Reload</button>
            </div>
        );
    }
}
