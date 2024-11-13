import gql from 'graphql-tag';

const ForcePublishAllMutation = gql`
    mutation forcePublishAll($path: String!) {
        jcr {
            mutateNode(pathOrId:$path) {
                forcePublish
            }
        }
    }
`;

export {ForcePublishAllMutation};
