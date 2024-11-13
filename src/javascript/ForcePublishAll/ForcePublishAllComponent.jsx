import React, {useContext} from 'react';
import ForcePublishAll from './ForcePublishAll';
import {ComponentRendererContext, registry} from '@jahia/ui-extender';
import {useNodeChecks} from '@jahia/data-helper';
import PropTypes from 'prop-types';
import {ForcePublishAllMutation} from "./forcePublishAll.gql-mutation";
import {useApolloClient, useMutation} from "@apollo/react-hooks";


const triggerRefetch = (name, queryParams) => {
    const refetch = registry.get('refetcher', name);
    if (!refetch) {
        return;
    }

    if (queryParams) {
        refetch.refetch(queryParams);
    } else {
        refetch.refetch();
    }
};

const triggerRefetchAll = () => {
    registry.find({type: 'refetcher'}).forEach(refetch => triggerRefetch(refetch.key));
};

export const ForcePublishAllActionComponent = ({path, render: Render, loading: Loading, ...others}) => {
    const componentRenderer = useContext(ComponentRendererContext);
    const res = useNodeChecks({path}, {...others, requiredPermission: ['publish','site-admin']});
    const client = useApolloClient();
    const [mutation, {called: mutationLoading}] = useMutation(ForcePublishAllMutation);
    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    const hanleClose = () => {
        mutation({
            variables: {
                path: path
            }
        }).then(() => {
            componentRenderer.setProperties('forcePublishAllDialog', {isOpen: false});
        }).then(() => {
            client.cache.flushNodeEntryByPath(path);
            triggerRefetchAll();
        });
    };
    return (
        <Render
            {...others}
            isVisible={res.checksResult}
            onClick={() => {
                componentRenderer.render('forcePublishAllDialog', ForcePublishAll, {
                        isOpen: true,
                        path: res.node.path,
                        onClose: () => {
                            hanleClose();
                        },
                        onExit: () => {
                            componentRenderer.destroy('forcePublishAllDialog');
                        }
                    }
                );
            }}
        />
    );
};

ForcePublishAllActionComponent.propTypes = {
    path: PropTypes.string,
    render: PropTypes.func.isRequired,
    loading: PropTypes.func
};
