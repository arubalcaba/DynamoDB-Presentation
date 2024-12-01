export interface Customer {
    email: string;
    firstName: string;
    lastName: string;
    phoneNumber: string;
}

export interface ImportMetaEnv {
    readonly VITE_API_HOST: string
}

export interface ImportMeta {
    readonly env: ImportMetaEnv
}